package chat;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author li
 * @version 1.0
 * @date 2019-05-28 00:04
 **/
public class MultiChatServer {

    private static ConcurrentHashMap<String, PrintWriter> onLineUsers = new ConcurrentHashMap<>();

    private static void broadcast(String msg) {
        for (PrintWriter out : onLineUsers.values()) {
            out.println(msg);
        }
    }

    static class ChatHandler implements Runnable {
        Socket client;

        ChatHandler(Socket client) {
            this.client = client;
        }

        @Override
        public void run() {
            try {
                InputStream is = client.getInputStream();
                OutputStream os = client.getOutputStream();

                Scanner in = new Scanner(new InputStreamReader(is, "UTF-8"));
                PrintWriter out = new PrintWriter(new OutputStreamWriter(os, "UTF-8"), true);
                // 等待用户发送id
                String username;
                while (true) {
                    username = in.nextLine();
                    if (!onLineUsers.containsKey(username)) {
                        onLineUsers.put(username, out);
                        out.println("Hello, " + username + "! If you want to exit, enter 'bye'.");
                        break;
                    } else {
                        out.println("Sorry! This login name had already been used. Please try again.");
                    }

                }

                boolean done = false;
                StringBuilder sb = new StringBuilder();
                while (!done) {
                    if (in.hasNextLine()) {
                        String msg = in.nextLine();
                        sb.delete(0, sb.length());
                        sb.append("[user@").append(username).append("] ").append(msg);
                        broadcast(sb.toString());
                        if (msg.trim().endsWith("bye")) {
                            done = true;
                            onLineUsers.remove(username);
                        }
                    }
                    out.flush();
                }
                in.close();
                out.close();
                client.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    public static void main(String[] args) {
        BlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(10);
        ThreadFactory threadFactory = new ThreadFactory() {
            private volatile AtomicInteger ai = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "worker-" + ai.getAndIncrement());
            }
        };

        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
                5,
                10,
                60,
                TimeUnit.SECONDS,
                workQueue, threadFactory
        );

        try (ServerSocket serverSocket = new ServerSocket(8080)) {

            System.out.println("Server started on port: 8080");
            int i = 1;
            //noinspection InfiniteLoopStatement
            while (true) {
                Socket client = serverSocket.accept();
                System.out.println("Spawning " + i++);
                Runnable r = new ChatHandler(client);
                threadPoolExecutor.execute(r);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
