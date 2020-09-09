package file;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author li
 * @version 1.0
 * @date 2019-05-26 19:27
 **/
public class FileServer {
    static class FileHandler implements Runnable {
        Socket client;
        FileHandler(Socket client) {
            this.client = client;
        }

        @Override
        public void run() {
            DataInputStream dataInputStream = null;
            FileOutputStream fileOutputStream = null;
            PrintWriter printWriter = null;
            try {

                dataInputStream = new DataInputStream(client.getInputStream());
                String filename = dataInputStream.readUTF();
                fileOutputStream = new FileOutputStream(new File(filename));

                byte[] bytes = new byte[1024];
                int len;
                while ((len = dataInputStream.read(bytes)) != -1) {
                    fileOutputStream.write(bytes, 0, len);
                }

                printWriter = new PrintWriter(new OutputStreamWriter(client.getOutputStream()), true);
                printWriter.println("接收完成！");
                printWriter.flush();
                System.out.println("接收到 " + filename);

            } catch (Exception e) {
                e.printStackTrace();
            }finally {
                try {
                    if (dataInputStream != null) {
                        dataInputStream.close();
                    }
                    if (fileOutputStream != null) {
                        fileOutputStream.close();
                    }
                    if (printWriter != null) {
                        printWriter.close();
                    }
                    client.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }

    }

    public static void main(String[] args) throws IOException {
        BlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(10);
        ThreadFactory threadFactory = new ThreadFactory() {
            private volatile AtomicInteger ai = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "worker-" + ai.getAndIncrement());
            }
        };
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(5, 10, 60, TimeUnit.SECONDS, workQueue, threadFactory);

        try (ServerSocket serverSocket = new ServerSocket(8080)) {
            System.out.println("Server started on port: 8080");
            int i = 1;
            //noinspection InfiniteLoopStatement
            while (true) {
                Socket client = serverSocket.accept();
                System.out.println("Spawning " + i++);
                Runnable r = new FileHandler(client);
                threadPoolExecutor.execute(r);
            }


        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            threadPoolExecutor.shutdown();
        }

    }
}
