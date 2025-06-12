package com.malskyi.studying.multithreading.fuel_player;

import javazoom.jl.decoder.*;
import javazoom.jl.player.AudioDevice;
import javazoom.jl.player.Player;

import javax.sound.sampled.Clip;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Scanner;

public class FuelPlayerDemo {
    private static final String FUEL_PATH = "music/Metallica-Fuel.mp3";
    private static final double FRAMES_PER_SECOND = 38.359;
    private static boolean isPreviewEnabled = false;

    private interface FuelPlayer {
        void start();

        void start(int startTimeMillis, int endTimeMillis);

        void pause();

        void resume();

        void stop();

        boolean isPlaying();
    }

    public static class AdvancedAudioPlayer extends Player {
        private boolean isPaused = false;

        public AdvancedAudioPlayer(InputStream stream) throws JavaLayerException {
            super(stream);
        }

        public void pause() {
            this.isPaused = true;
        }

        public void resume() {
            this.isPaused = false;
            synchronized (this) {
                this.notify();
            }
        }

        @Override
        public boolean play(int frames) throws JavaLayerException {
            try {
                Field audioField = getClass().getSuperclass().getDeclaredField("audio");
                Field completeField = getClass().getSuperclass().getDeclaredField("complete");
                Field closedField = getClass().getSuperclass().getDeclaredField("closed");
                audioField.setAccessible(true);
                completeField.setAccessible(true);
                closedField.setAccessible(true);

                boolean ret = true;

                int frameNumber = 0;
                while (frames-- > 0 && ret) {
                    synchronized (this) {
                        if (isPaused) {
                            wait();
                        }
                    }
                    ret = decodeFrame(frameNumber);
                    frameNumber++;
                }

                if (!ret) {
                    AudioDevice out = (AudioDevice) audioField.get(this);
                    if (out != null) {
                        out.flush();
                        synchronized (this) {
                            completeField.set(this, (!(boolean) closedField.get(this)));
                            close();
                        }
                    }
                }
                System.out.println("frameNumber=" + frameNumber);
                return ret;
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        public boolean play(int startFrame, int endFrame) throws JavaLayerException {
            int frames = endFrame - startFrame;
            try {
                Field audioField = getClass().getSuperclass().getDeclaredField("audio");
                Field completeField = getClass().getSuperclass().getDeclaredField("complete");
                Field closedField = getClass().getSuperclass().getDeclaredField("closed");
                audioField.setAccessible(true);
                completeField.setAccessible(true);
                closedField.setAccessible(true);

                boolean ret = true;

                int currentFrameNumber = 0;
                while (frames-- > 0 && ret) {
                    synchronized (this) {
                        if (isPaused) {
                            wait();
                        }
                    }
                    ret = decodeFrame(currentFrameNumber, startFrame);
                    currentFrameNumber++;
                }

                if (!ret) {
                    AudioDevice out = (AudioDevice) audioField.get(this);
                    if (out != null) {
                        out.flush();
                        synchronized (this) {
                            completeField.set(this, (!(boolean) closedField.get(this)));
                            close();
                        }
                    }
                }
                System.out.println("frameNumber=" + currentFrameNumber);
                return ret;
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        protected boolean decodeFrame(int frameNumber) throws JavaLayerException {
            return decodeFrame(frameNumber, 0);
        }

        private boolean decodeFrame(int frameNumber, int framesToSkip) throws JavaLayerException {
            try {
                Field bitstreamField = getClass().getSuperclass().getDeclaredField("bitstream");
                Field audioField = getClass().getSuperclass().getDeclaredField("audio");
                Field decoderField = getClass().getSuperclass().getDeclaredField("decoder");


                audioField.setAccessible(true);
                bitstreamField.setAccessible(true);
                decoderField.setAccessible(true);

                try {
                    AudioDevice audio = (AudioDevice) audioField.get(this);
                    Bitstream bitstream = (Bitstream) bitstreamField.get(this);
                    Decoder decoder = (Decoder) decoderField.get(this);
                    AudioDevice out = audio;
                    if (out == null)
                        return false;

                    Header h = bitstream.readFrame();

                    if (h == null)
                        return false;

                    if (frameNumber < framesToSkip) {
//                        decoder.decodeFrame(h, bitstream);
                        bitstream.closeFrame();
                        return true;
                    }

                    // sample buffer set when decoder constructed
                    SampleBuffer output = (SampleBuffer) decoder.decodeFrame(h, bitstream);

                    synchronized (this) {
                        out = audio;
                        if (out != null) {
                            out.write(output.getBuffer(), 0, output.getBufferLength());
                        }
                    }

                    bitstream.closeFrame();
                } catch (RuntimeException ex) {
                    throw new JavaLayerException("Exception decoding audio frame", ex);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                } catch (DecoderException e) {
                    throw new RuntimeException(e);
                } catch (JavaLayerException e) {
                    throw new RuntimeException(e);
                }
            } catch (NoSuchFieldException e) {
                throw new RuntimeException(e);
            }

            return true;
        }
    }

    // todo imrove this application - make it more complex via additional thread loading music from cloud maybe?
    // todo add a thread for lyrics
    public static class FuelPlayerJLayer implements FuelPlayer {
        private boolean isRunning = true;
        private boolean isPaused = false;
        private AdvancedAudioPlayer player;
        private PlayerRunThread playerRunThread;

        public FuelPlayerJLayer() {
            try {
                this.player = initPlayer();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }

        private AdvancedAudioPlayer initPlayer() throws Exception {
            this.isPaused = false;
            ClassLoader classloader = Thread.currentThread().getContextClassLoader();
            File audioFile = new File(classloader.getResource(FUEL_PATH).toURI());
            FileInputStream fileInputStream = new FileInputStream(audioFile);
            return new AdvancedAudioPlayer(fileInputStream);
        }

        @Override
        public void start() {
            try {
                this.player = initPlayer();
                this.playerRunThread = new PlayerRunThread(0);
                this.playerRunThread.start();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            System.out.println("Now playing!");
        }

        private class PlayerRunThread extends Thread {
            private final int startFrame;
            private final int endFrame;

            private PlayerRunThread(int startFrame) {
                this.startFrame = startFrame;
                this.endFrame = Integer.MAX_VALUE;
            }

            private PlayerRunThread(int startFrame, int endFrame) {
                this.startFrame = startFrame;
                this.endFrame = endFrame;
            }

            @Override
            public void run() {
                try {
                    player.play(startFrame, endFrame);
                } catch (JavaLayerException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        @Override
        public void start(int startTimeMillis, int endTimeMillis) {
            try {
                this.player = initPlayer();
                int startFrame = startTimeMillis > 0 ? (int) (startTimeMillis / FRAMES_PER_SECOND) : 0;
                int endFrame = (int) (endTimeMillis / FRAMES_PER_SECOND);
                this.playerRunThread = new PlayerRunThread(startFrame, endFrame);
                this.playerRunThread.start();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void pause() {
            this.isPaused = true;
            player.pause();
        }

        @Override
        public void resume() {
            this.isPaused = false;
            this.player.resume();
        }

        @Override
        public void stop() {
            player.close();
        }

        @Override
        public boolean isPlaying() {
            return player != null && !player.isComplete();
        }
    }


    // 1. add IO
    // 2. add processing thread
    // 3. add playback thread
    // 4. add player - maybe some library?
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        FuelPlayer fuelPlayer = new FuelPlayerJLayer();

        if (isPreviewEnabled) {
            fuelPlayer.start();
        }


        System.out.println("Welcome to Fuel! >:D");
        if (isPreviewEnabled) {
            System.out.println("Press 1 to disable preview");
        } else {
            System.out.println("Press 1 to enable preview");
        }
        System.out.println("Press 2 to pause");
        System.out.println("Press 3 to resume");
        System.out.println("Press 4 for section selection");
        System.out.println("Press -1 to exit");

        showMainMenu(scanner, fuelPlayer);
    }

    private static void showMainMenu(Scanner scanner, FuelPlayer fuelPlayer) {
        while (true) {
            int choice = scanner.nextInt();

            switch (choice) {
                case 1:
                    if (isPreviewEnabled) {
                        fuelPlayer.stop();
                        isPreviewEnabled = false;
                    } else {
                        fuelPlayer.start();
                        isPreviewEnabled = true;
                    }
                    break;
                case 2:
                    fuelPlayer.pause();
                    break;
                case 3:
                    fuelPlayer.resume();
                    break;
                case 4:
                    showSectionsSelection(scanner, fuelPlayer);
                    break;
                case -1:
                    fuelPlayer.stop();
                    System.exit(0);
                    break;
                default:
                    System.out.println("Choose valid option!");
                    break;
            }
        }
    }

    private static void showSectionsSelection(Scanner scanner, FuelPlayer fuelPlayer) {
        System.out.println("1. Give me fuel, give me fire, give my what I so desire, OOOH");
        System.out.println("2. *guitar solo*"); // 00:14 - 00:18
        System.out.println("3. war hogs, war head, fuck em man"); // 00:41 - 00:44
        System.out.println("4. OOOOOOOH WANNA BURN FUEL'S PUMPIN' ENGINES BURNING WHOLES"); // 00:53 - 01:14
        System.out.println("5. OOOOOOOH WANNA BURN FUEL'S PUMPIN' ENGINES BURNING WHOLES"); // 02:08 - 02:28
        System.out.println("6. *guitar solo long*"); //02:33 - 03:35
        System.out.println("-1. exit");
        while (true) {
            int choice = scanner.nextInt();
            switch (choice) {
                case 1:
                    fuelPlayer.start(0, 4800);
                    break;
                case 2:
                    fuelPlayer.start(14000, 18000);
                    break;
                case 3:
                    fuelPlayer.start(41000, 44000);
                    break;
                case 4:
                    fuelPlayer.start(53000, 74000);
                    break;
                case 5:
                    fuelPlayer.start(128000, 148000);
                    break;
                case 6:
                    fuelPlayer.start(153000, 215000);
                    break;
                case -1:
                    return;
                default:
                    System.out.println("Select valid option!");
            }
        }
    }
}
