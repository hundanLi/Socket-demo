package chat;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.UUID;

/**
 * @author li
 * @version 1.0
 * @date 2019-05-28 00:15
 **/
public class ChatClient {
    private static String username;

    static class SendMsgTask implements Runnable{
        Scanner in;
        PrintWriter out;
        SendMsgTask(InputStream is, OutputStream os) {
            this.in = new Scanner(is);
            this.out = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8), true);
        }

        @Override
        public void run() {
            // 传输用户id
            out.println(username);
            while (in.hasNextLine()) {
                String line = in.nextLine();
                out.println(line);
                out.flush();
            }
        }
    }
    public static void main(String[] args) {

        String host = "localhost";
        int port = 8080;
        username = UUID.randomUUID().toString().substring(20);

        if (args != null && args.length > 0) {
            for (String arg : args) {
                if (arg.startsWith("--host=")) {
                    host = arg.substring("--host=".length());
                } else if (arg.startsWith("--port=")) {
                    port = Integer.parseInt(arg.substring("--port=".length()));
                } else {
                    username = arg;
                }
            }
        }

        try {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), 3000);

            // 副线程发送消息
            SendMsgTask sendMsgTask = new SendMsgTask(System.in, socket.getOutputStream());
            //noinspection AlibabaAvoidManuallyCreateThread
            Thread sendMsgThread = new Thread(sendMsgTask);
            sendMsgThread.setDaemon(true);
            sendMsgThread.start();

            // 主线程接收消息
            Scanner serverMsg = new Scanner(socket.getInputStream(), "UTF-8");
            while (!socket.isClosed()) {
                try {
                    socket.sendUrgentData(0);
                } catch (Exception e) {
                    System.out.println("Disconnected");
                    break;
                }

                if (serverMsg.hasNextLine()) {
                    String line = serverMsg.nextLine();
                    System.out.println(line);
                }

            }
            serverMsg.close();
            socket.close();
        } catch (FileNotFoundException e) {
            System.out.println("File Not Exist：　" + e.getMessage());
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}
