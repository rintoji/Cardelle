import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class VideoRenderer {

    @FunctionalInterface
    public interface RenderListener {
        void onProgress(String statusMessage, double progress);
    }

    private final TransitionManager transitionManager;

    public VideoRenderer(TransitionManager transitionManager) {
        this.transitionManager = (transitionManager != null) ? transitionManager : new TransitionManager();
    }

    public TransitionManager getTransitionManager() {
        return transitionManager;
    }

    public File resolveOutputFile(List<MediaManager.SlideItem> slides, File mp3File, String requestedName) {
        String raw = (requestedName == null) ? "" : requestedName.trim();
        String cleaned = raw.replaceAll("[/\\\\]+", "_").replaceAll("[^a-zA-Z0-9._\\- ]", "_").trim();
        if (cleaned.isEmpty()) {
            cleaned = "cardelle_output";
        }
        if (!cleaned.toLowerCase(Locale.ROOT).endsWith(".mp4")) {
            cleaned = cleaned + ".mp4";
        }

        String home = System.getProperty("user.home", ".");
        Path videosDir = Paths.get(home, "Videos");
        Path musicDir = Paths.get(home, "Music");

        if (Files.isDirectory(videosDir)) {
            return videosDir.resolve(cleaned).toFile();
        }
        if (mp3File != null && mp3File.getParentFile() != null && mp3File.getParentFile().isDirectory()) {
            return new File(mp3File.getParentFile(), cleaned);
        }
        if (slides != null) {
            for (MediaManager.SlideItem item : slides) {
                if (item != null && item.imageFile() != null && item.imageFile().getParentFile() != null
                        && item.imageFile().getParentFile().isDirectory()) {
                    return new File(item.imageFile().getParentFile(), cleaned);
                }
            }
        }
        if (Files.isDirectory(musicDir)) {
            return musicDir.resolve(cleaned).toFile();
        }
        return new File(cleaned);
    }

    public double probeAudioDurationSeconds(File mp3File) throws Exception {
        if (mp3File == null || !mp3File.exists() || !mp3File.isFile()) {
            throw new IllegalArgumentException("A valid MP3 file is required.");
        }

        ProcessBuilder pb = new ProcessBuilder(
                "ffprobe",
                "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                mp3File.getAbsolutePath()
        );
        pb.redirectErrorStream(true);

        Process process = pb.start();
        String line;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            line = reader.readLine();
        }

        int exitCode = process.waitFor();
        if (exitCode != 0 || line == null || line.isBlank()) {
            throw new IllegalStateException("ffprobe failed with exit code " + exitCode);
        }

        double duration = Double.parseDouble(line.trim());
        if (duration <= 0) {
            throw new IllegalStateException("Invalid audio duration: " + duration);
        }
        return duration;
    }

    public void render(
            List<MediaManager.SlideItem> slides,
            File mp3File,
            File outputFile,
            double secondsPerSlide,
            RenderListener listener
    ) throws Exception {
        render(slides, mp3File, outputFile, secondsPerSlide, false, false, listener);
    }

    public void render(
            List<MediaManager.SlideItem> slides,
            File mp3File,
            File outputFile,
            double secondsPerSlide,
            boolean blurTrack,
            RenderListener listener
    ) throws Exception {
        render(slides, mp3File, outputFile, secondsPerSlide, blurTrack, false, listener);
    }

    public void render(
            List<MediaManager.SlideItem> slides,
            File mp3File,
            File outputFile,
            double secondsPerSlide,
            boolean blurTrack,
            boolean includeLogo,
            RenderListener listener
    ) throws Exception {
        if (slides == null || slides.isEmpty()) {
            throw new IllegalArgumentException("Add images or color cards (Ctrl+N) before rendering.");
        }
        if (mp3File == null || !mp3File.exists()) {
            throw new IllegalArgumentException("An MP3 file is required to render.");
        }
        if (outputFile == null) {
            throw new IllegalArgumentException("Output MP4 file path is required.");
        }

        emit(listener, "Measuring MP3 length...", 0.04);
        double audioDuration = probeAudioDurationSeconds(mp3File);
        double slideDur = Math.max(1.0, secondsPerSlide);
        double xfadeDur = Math.min(transitionManager.getTransitionDuration(), slideDur * 0.35);
        double step = Math.max(0.4, slideDur - xfadeDur);

        int totalSlices = Math.max(1, (int) Math.ceil((audioDuration - xfadeDur) / step) + 1);
        double clipDur = slideDur + xfadeDur;

        Path workDir = Files.createTempDirectory("cardelle_render_");
        List<File> clipFiles = new ArrayList<>(totalSlices);
        List<TransitionManager.EditorTransition> transitions = new ArrayList<>();

        try {
            for (int i = 0; i < totalSlices; i++) {
                MediaManager.SlideItem item = slides.get(i % slides.size());
                File clipFile = workDir.resolve(String.format(Locale.ROOT, "clip_%03d.mp4", i)).toFile();

                if (i > 0) {
                    transitions.add(transitionManager.nextRandomTransition());
                }

                double prepProg = 0.05 + 0.45 * ((double) i / totalSlices);
                emit(listener, String.format(
                        Locale.ROOT,
                        "Slide %d/%d (%s)%s%s...",
                        i + 1, totalSlices, item.displayName(),
                        blurTrack ? " [Blur]" : "",
                        includeLogo ? " [Logo]" : ""
                ), prepProg);

                if (item.isColorCard()) {
                    renderColorClip(item.solidColor(), clipFile, clipDur);
                } else {
                    File img = item.imageFile();
                    if (img == null || !img.exists()) {
                        throw new IllegalStateException("Missing image file: " + img);
                    }
                    renderImageClip(img, clipFile, clipDur);
                }
                clipFiles.add(clipFile);
            }

            if (outputFile.getParentFile() != null) {
                Files.createDirectories(outputFile.getParentFile().toPath());
            }

            File assembledVideo;
            if (clipFiles.size() == 1) {
                assembledVideo = clipFiles.get(0);
            } else {
                int batchSize = 8;
                List<File> batchOutputs = new ArrayList<>();
                int totalBatches = Math.max(1, (int) Math.ceil((double) clipFiles.size() / (batchSize - 1)));

                int idx = 0;
                int batchNum = 0;
                while (idx < clipFiles.size()) {
                    int end = Math.min(clipFiles.size(), idx + batchSize);
                    List<File> subClips = clipFiles.subList(idx, end);
                    List<TransitionManager.EditorTransition> subTrans = new ArrayList<>();
                    for (int k = idx; k < end - 1; k++) {
                        subTrans.add(transitions.get(k));
                    }

                    File batchOut = workDir.resolve(String.format(Locale.ROOT, "batch_%02d.mp4", batchNum)).toFile();
                    double batchProg = 0.50 + 0.30 * ((double) (batchNum + 1) / totalBatches);
                    batchNum++;
                    emit(listener, String.format(Locale.ROOT, "Transitions batch %d/%d...", batchNum, totalBatches), batchProg);

                    if (subClips.size() == 1) {
                        Files.copy(subClips.get(0).toPath(), batchOut.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        runXfadeBatch(subClips, subTrans, clipDur, xfadeDur, batchOut);
                    }
                    batchOutputs.add(batchOut);

                    idx = (end >= clipFiles.size()) ? clipFiles.size() : end - 1;
                }

                emit(listener, "Joining transitions...", 0.84);
                File concatFile = createConcatList(workDir, batchOutputs);
                assembledVideo = workDir.resolve("assembled.mp4").toFile();
                List<String> concatCmd = List.of(
                        "ffmpeg", "-y",
                        "-f", "concat",
                        "-safe", "0",
                        "-i", concatFile.getAbsolutePath(),
                        "-c:v", "copy",
                        assembledVideo.getAbsolutePath()
                );
                runCommand(concatCmd);
            }

            File logoFile = includeLogo ? transitionManager.writeCardelleLogoPng(workDir) : null;

            emit(listener, "Finishing video + muxing MP3...", 0.92);
            muxFinalOutput(assembledVideo, mp3File, logoFile, outputFile, audioDuration, blurTrack);

            emit(listener, "Saved: " + outputFile.getAbsolutePath()
                    + (blurTrack ? " [Full Blur]" : "")
                    + (includeLogo ? " [Cardelle Logo]" : "")
                    + " • " + transitionManager.describeTransitions(transitions), 1.0);
        } finally {
            cleanupTempDir(workDir);
        }
    }

    private void renderImageClip(File image, File outClip, double durationSeconds) throws Exception {
        String vf = transitionManager.buildImageFilterGraph();
        List<String> cmd = List.of(
                "ffmpeg", "-y",
                "-loop", "1",
                "-i", image.getAbsolutePath(),
                "-t", String.format(Locale.ROOT, "%.3f", durationSeconds),
                "-vf", vf,
                "-c:v", "libx264",
                "-preset", "fast",
                "-crf", "18",
                "-pix_fmt", "yuv420p",
                "-an",
                outClip.getAbsolutePath()
        );
        runCommand(cmd);
    }

    private void renderColorClip(Color color, File outClip, double durationSeconds) throws Exception {
        String fc = transitionManager.buildColorCardFilterGraph(color, durationSeconds);
        List<String> cmd = List.of(
                "ffmpeg", "-y",
                "-filter_complex", fc,
                "-t", String.format(Locale.ROOT, "%.3f", durationSeconds),
                "-c:v", "libx264",
                "-preset", "fast",
                "-crf", "18",
                "-pix_fmt", "yuv420p",
                "-an",
                outClip.getAbsolutePath()
        );
        runCommand(cmd);
    }

    private void runXfadeBatch(
            List<File> clips,
            List<TransitionManager.EditorTransition> batchTransitions,
            double clipDur,
            double xfadeDur,
            File outFile
    ) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg");
        cmd.add("-y");
        for (File c : clips) {
            cmd.add("-i");
            cmd.add(c.getAbsolutePath());
        }

        StringBuilder fc = new StringBuilder();
        String prev = "[0:v]";
        double offset = Math.max(0.1, clipDur - xfadeDur);

        for (int i = 1; i < clips.size(); i++) {
            String next = "[" + i + ":v]";
            String out = (i == clips.size() - 1) ? "[vout]" : "[vx" + i + "]";
            String tr = batchTransitions.get(i - 1).getFfmpegXfade();

            fc.append(prev)
                    .append(next)
                    .append(String.format(
                            Locale.ROOT,
                            "xfade=transition=%s:duration=%.3f:offset=%.3f",
                            tr, xfadeDur, offset
                    ))
                    .append(out)
                    .append(";\n");

            prev = out;
            offset += Math.max(0.1, clipDur - xfadeDur);
        }

        cmd.add("-filter_complex");
        cmd.add(fc.toString());
        cmd.add("-map");
        cmd.add("[vout]");
        cmd.add("-c:v");
        cmd.add("libx264");
        cmd.add("-preset");
        cmd.add("fast");
        cmd.add("-crf");
        cmd.add("18");
        cmd.add("-pix_fmt");
        cmd.add("yuv420p");
        cmd.add("-an");
        cmd.add(outFile.getAbsolutePath());

        runCommand(cmd);
    }

    private void muxFinalOutput(
            File videoClip,
            File mp3File,
            File logoFile,
            File outputFile,
            double audioDuration,
            boolean blurTrack
    ) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg");
        cmd.add("-y");
        cmd.add("-i");
        cmd.add(videoClip.getAbsolutePath());
        cmd.add("-i");
        cmd.add(mp3File.getAbsolutePath());

        boolean hasLogo = (logoFile != null && logoFile.exists());
        if (hasLogo) {
            // A PNG is a single frame by default. Loop it so the watermark is
            // present for the entire video, not just the first frame.
            cmd.add("-loop");
            cmd.add("1");
            cmd.add("-i");
            cmd.add(logoFile.getAbsolutePath());
        }

        cmd.add("-t");
        cmd.add(String.format(Locale.ROOT, "%.3f", audioDuration));

        if (hasLogo) {
            String baseFilter = blurTrack
                    ? "[0:v]" + transitionManager.buildFullTrackBlurFilter() + "[vbase]"
                    : "[0:v]setsar=1[vbase]";
            String fc = baseFilter
                    + ";[2:v]format=rgba, scale=180:-1:flags=lanczos[logo];"
                    + "[vbase][logo]overlay=(W-w)/2:(H-h)/2:format=auto,setsar=1[vfinal]";
            cmd.add("-filter_complex");
            cmd.add(fc);
            cmd.add("-map");
            cmd.add("[vfinal]");
            cmd.add("-map");
            cmd.add("1:a");
            cmd.add("-c:v");
            cmd.add("libx264");
            cmd.add("-preset");
            cmd.add("fast");
            cmd.add("-crf");
            cmd.add("18");
            cmd.add("-pix_fmt");
            cmd.add("yuv420p");
        } else if (blurTrack) {
            cmd.add("-vf");
            cmd.add(transitionManager.buildFullTrackBlurFilter());
            cmd.add("-map");
            cmd.add("0:v");
            cmd.add("-map");
            cmd.add("1:a");
            cmd.add("-c:v");
            cmd.add("libx264");
            cmd.add("-preset");
            cmd.add("fast");
            cmd.add("-crf");
            cmd.add("18");
            cmd.add("-pix_fmt");
            cmd.add("yuv420p");
        } else {
            cmd.add("-map");
            cmd.add("0:v");
            cmd.add("-map");
            cmd.add("1:a");
            cmd.add("-c:v");
            cmd.add("copy");
        }

        cmd.addAll(List.of(
                "-c:a", "aac",
                "-b:a", "192k",
                "-shortest",
                outputFile.getAbsolutePath()
        ));
        runCommand(cmd);
    }

    private File createConcatList(Path workDir, List<File> files) throws Exception {
        File listFile = workDir.resolve("concat.txt").toFile();
        StringBuilder sb = new StringBuilder();
        for (File f : files) {
            sb.append("file '").append(f.getAbsolutePath().replace("'", "'\\''")).append("'\n");
        }
        Files.writeString(listFile.toPath(), sb.toString(), StandardCharsets.UTF_8);
        return listFile;
    }

    public void openVideoWindow(File videoFile) {
        if (videoFile == null || !videoFile.exists()) {
            return;
        }

        List<List<String>> candidates = List.of(
                List.of("mpv", "--loop=inf", videoFile.getAbsolutePath()),
                List.of("ffplay", "-autoexit", "-loop", "0", "-window_title", "Cardelle Player", videoFile.getAbsolutePath()),
                List.of("xdg-open", videoFile.getAbsolutePath())
        );

        for (List<String> cmd : candidates) {
            if (!isCommandAvailable(cmd.get(0))) {
                continue;
            }
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.inheritIO();
                pb.start();
                return;
            } catch (Exception ignored) {
            }
        }
    }

    private boolean isCommandAvailable(String binary) {
        try {
            Process check = new ProcessBuilder("sh", "-c", "command -v " + binary).start();
            return check.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void runCommand(List<String> cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder log = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (log.length() < 4000) {
                    log.append(line).append('\n');
                }
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("FFmpeg exited with code " + exitCode + ":\n" + log);
        }
    }

    private void cleanupTempDir(Path workDir) {
        try {
            if (Files.exists(workDir)) {
                try (var stream = Files.walk(workDir)) {
                    stream.sorted((a, b) -> b.compareTo(a))
                            .forEach(p -> {
                                try {
                                    Files.deleteIfExists(p);
                                } catch (Exception ignored) {
                                }
                            });
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void emit(RenderListener listener, String msg, double progress) {
        if (listener != null) {
            listener.onProgress(msg, Math.max(0.0, Math.min(1.0, progress)));
        }
    }
}