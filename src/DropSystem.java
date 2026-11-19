import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DropSystem {

    private final MediaManager mediaManager;
    private final JLabel statusLabel;

    public DropSystem(MediaManager mediaManager, JLabel statusLabel) {
        this.mediaManager = mediaManager;
        this.statusLabel = statusLabel;
    }

    public void attachTo(Component target) {
        if (target == null) {
            return;
        }

        new DropTarget(target, DnDConstants.ACTION_COPY_OR_MOVE, new DropTargetAdapter() {

            @Override
            public void dragEnter(DropTargetDragEvent dtde) {
                if (hasFileFlavor(dtde.getCurrentDataFlavors())) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY);
                } else {
                    dtde.rejectDrag();
                }
            }

            @Override
            public void dragOver(DropTargetDragEvent dtde) {
                if (hasFileFlavor(dtde.getCurrentDataFlavors())) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY);
                } else {
                    dtde.rejectDrag();
                }
            }

            @Override
            public void drop(DropTargetDropEvent dtde) {
                try {
                    if (!hasFileFlavor(dtde.getCurrentDataFlavors())) {
                        dtde.rejectDrop();
                        updateStatus("Drop not a file list/URI");
                        return;
                    }

                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    Transferable transferable = dtde.getTransferable();
                    List<File> droppedFiles = extractFiles(transferable);

                    if (droppedFiles.isEmpty()) {
                        dtde.dropComplete(false);
                        updateStatus("No files received from drop");
                        return;
                    }

                    String summary = (mediaManager != null)
                            ? mediaManager.acceptFiles(droppedFiles)
                            : "Dropped " + droppedFiles.size() + " file(s)";

                    updateStatus(summary);
                    dtde.dropComplete(true);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    updateStatus("Drop failed: " + ex.getClass().getSimpleName());
                    dtde.dropComplete(false);
                }
            }
        }, true);
    }

    private void updateStatus(String message) {
        if (statusLabel != null) {
            SwingUtilities.invokeLater(() -> statusLabel.setText(message));
        }
    }

    private boolean hasFileFlavor(DataFlavor[] flavors) {
        if (flavors == null) {
            return false;
        }
        for (DataFlavor flavor : flavors) {
            if (flavor == null) {
                continue;
            }
            if (flavor.equals(DataFlavor.javaFileListFlavor)) {
                return true;
            }
            String mime = flavor.getMimeType().toLowerCase(Locale.ROOT);
            if (mime.startsWith("text/uri-list")) {
                return true;
            }
        }
        return false;
    }

    private List<File> extractFiles(Transferable transferable) {
        List<File> files = new ArrayList<>();
        if (transferable == null) {
            return files;
        }

        try {
            if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                Object data = transferable.getTransferData(DataFlavor.javaFileListFlavor);
                if (data instanceof List<?> list) {
                    for (Object entry : list) {
                        if (entry instanceof File f) {
                            files.add(f);
                        } else if (entry != null) {
                            File parsed = uriToFile(entry.toString());
                            if (parsed != null) {
                                files.add(parsed);
                            }
                        }
                    }
                }
                if (!files.isEmpty()) {
                    return files;
                }
            }
        } catch (Exception ignored) {
        }

        for (DataFlavor flavor : transferable.getTransferDataFlavors()) {
            if (flavor == null || !flavor.getMimeType().toLowerCase(Locale.ROOT).startsWith("text/uri-list")) {
                continue;
            }
            try {
                Object raw = transferable.getTransferData(flavor);
                String text = readText(raw);
                if (text != null && !text.isBlank()) {
                    for (String line : text.split("\\R")) {
                        String trimmed = line.replace("\r", "").trim();
                        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                            continue;
                        }
                        File parsed = uriToFile(trimmed);
                        if (parsed != null) {
                            files.add(parsed);
                        }
                    }
                }
                if (!files.isEmpty()) {
                    return files;
                }
            } catch (Exception ignored) {
            }
        }

        return files;
    }

    private String readText(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof String s) {
            return s;
        }
        if (raw instanceof Reader reader) {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(reader)) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                return sb.toString();
            } catch (Exception ignored) {
                return null;
            }
        }
        if (raw instanceof InputStream in) {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                return sb.toString();
            } catch (Exception ignored) {
                return null;
            }
        }
        return raw.toString();
    }

    private File uriToFile(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.replace("\r", "").trim();
        if (!cleaned.startsWith("file:")) {
            return null;
        }
        try {
            if (cleaned.startsWith("file://localhost/")) {
                cleaned = "file:///" + cleaned.substring("file://localhost/".length());
            }
            if (cleaned.startsWith("file://")) {
                return Paths.get(URI.create(cleaned)).toFile();
            }
            String decoded = URLDecoder.decode(cleaned.substring("file:".length()), StandardCharsets.UTF_8);
            return new File(decoded);
        } catch (Exception ex) {
            try {
                String stripped = cleaned.replaceFirst("^file:/+", "/");
                String decoded = URLDecoder.decode(stripped, StandardCharsets.UTF_8);
                return new File(decoded);
            } catch (Exception ignored) {
                return null;
            }
        }
    }
}