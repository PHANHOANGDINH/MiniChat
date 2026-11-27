package chat.client;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;


public class AudioRecorder {

    public static final AudioFormat FORMAT = new AudioFormat(16000f, 16, 1, true, false);
    public static final int MAX_SECONDS = 120;

    private TargetDataLine line;
    private ByteArrayOutputStream buffer;
    private Thread worker;

    public void start() throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
        if (!AudioSystem.isLineSupported(info))
            throw new LineUnavailableException("Không tìm thấy micro hỗ trợ định dạng ghi âm");
        line = (TargetDataLine) AudioSystem.getLine(info);
        line.open(FORMAT);
        line.start();
        buffer = new ByteArrayOutputStream();
        int maxBytes = (int) (FORMAT.getFrameRate() * FORMAT.getFrameSize() * MAX_SECONDS);
        worker = new Thread(() -> {
            byte[] chunk = new byte[4096];
            while (line.isOpen()) {
                int n = line.read(chunk, 0, chunk.length);
                if (n <= 0) break;
                buffer.write(chunk, 0, n);
                if (buffer.size() >= maxBytes) break;   // tự dừng khi quá dài
            }
        }, "recorder");
        worker.start();
    }

    /** Dừng ghi và trả về dữ liệu WAV hoàn chỉnh. */
    public byte[] stop() throws IOException {
        line.stop();
        line.close();
        try { worker.join(1000); } catch (InterruptedException ignored) { }
        byte[] pcm = buffer.toByteArray();
        return toWav(pcm);
    }

    public static byte[] toWav(byte[] pcm) throws IOException {
        AudioInputStream ais = new AudioInputStream(new ByteArrayInputStream(pcm), FORMAT,
                pcm.length / FORMAT.getFrameSize());
        ByteArrayOutputStream wav = new ByteArrayOutputStream();
        AudioSystem.write(ais, AudioFileFormat.Type.WAVE, wav);
        return wav.toByteArray();
    }

    /** Thời lượng (giây) của một tệp WAV. */
    public static double durationOf(byte[] wav) {
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav))) {
            return ais.getFrameLength() / ais.getFormat().getFrameRate();
        } catch (Exception e) {
            return 0;
        }
    }

    /** Phát một đoạn âm thanh WAV. */
    public static Clip play(byte[] wav) throws Exception {
        AudioInputStream ais = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav));
        Clip clip = AudioSystem.getClip();
        clip.open(ais);
        clip.addLineListener(ev -> {
            if (ev.getType() == javax.sound.sampled.LineEvent.Type.STOP) clip.close();
        });
        clip.start();
        return clip;
    }
}
