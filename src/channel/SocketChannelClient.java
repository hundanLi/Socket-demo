package channel;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Scanner;
import java.util.Set;
import java.util.UUID;

/**
 * @author li
 * @version 1.0
 * @date 2019-05-28 16:58
 **/
public class SocketChannelClient {
    private static String username;
    private static SocketChannel socketChannel;

    static class SendMsgTask implements Runnable {
        Scanner in;
        SocketChannel socketChannel;

        SendMsgTask(SocketChannel socketChannel, InputStream inputStream) {
            this.socketChannel = socketChannel;
            this.in = new Scanner(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        }

        @Override
        public void run() {
            try {
                StringBuilder sb = new StringBuilder();
                while (in.hasNextLine()) {
                    String line = in.nextLine();
                    sb.delete(0, sb.length());
                    sb.append("[user@").append(username).append("]$ ").append(line);

                    String msg = sb.toString();
                    sendMsg(msg);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void sendMsg(String msg) throws IOException {
            ByteBuffer byteBuffer;
            byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);
            byteBuffer = ByteBuffer.allocate(bytes.length);
            byteBuffer.put(bytes);

            byteBuffer.flip();
            socketChannel.write(byteBuffer);
            byteBuffer.clear();
        }
    }

    public static void main(String[] args) throws IOException {
        String host = "localhost";
        int port = 8080;
        username = UUID.randomUUID().toString().substring(20);

        // 读取命令行参数
        if (args != null && args.length > 0) {
            for (String arg : args) {
                if (arg.startsWith("-host=")) {
                    host = arg.substring("-host=".length());
                } else if (arg.startsWith("-port=")) {
                    port = Integer.parseInt(arg.substring("-port=".length()));
                } else {
                    username = arg;
                }
            }
        }

        try {

            // 创建socket连接
            Selector selector = Selector.open();
            socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(false);
            socketChannel.register(selector, SelectionKey.OP_CONNECT);
            socketChannel.connect(new InetSocketAddress(host, port));

            // 启动控制台监听线程，发送消息
            SendMsgTask sendMsgTask = new SendMsgTask(socketChannel, System.in);
            Thread thread = new Thread(sendMsgTask);
            thread.setDaemon(true);
            thread.start();


            ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
            StringBuilder sb = new StringBuilder();

            // 检查I/O就绪的通道并接收信息
            doSelect(selector, byteBuffer, sb);

        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            socketChannel.close();
        }
    }

    private static void doSelect(Selector selector, ByteBuffer byteBuffer, StringBuilder sb) throws IOException {
        Set<SelectionKey> selectionKeys;
        Iterator<SelectionKey> iterator;
        while (true) {
            // 每隔500ms检查I/O就绪的通道
            selector.select(500);
            selectionKeys = selector.selectedKeys();
            iterator = selectionKeys.iterator();
            while (iterator.hasNext()) {
                SelectionKey selectionKey = iterator.next();
                if (selectionKey.isConnectable()) {

                    SocketChannel connect = (SocketChannel) selectionKey.channel();
                    // 确保完成连接
                    connect.finishConnect();

                    // 传输用户名
                    sendUsername(connect);

                    connect.register(selector, SelectionKey.OP_READ);
                } else if (selectionKey.isReadable()) {

                    // 接收服务器发送的消息，如果连接断开，则退出
                    if (!receiveMsg(byteBuffer, sb, selectionKey)) {
                        return;
                    }
                }
                iterator.remove();
            }
            selectionKeys.clear();
        }
    }

    private static void sendUsername(SocketChannel connect) throws IOException {
        byte[] bytes;
        String msg = "login->" + username;
        bytes = msg.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(bytes.length);
        buffer.put(bytes);
        buffer.flip();
        connect.write(buffer);
        buffer.clear();
    }

    private static boolean receiveMsg(ByteBuffer byteBuffer,
                                      StringBuilder sb,
                                      SelectionKey selectionKey) throws IOException {
        SocketChannel channel;
        byte[] bytes;
        sb.delete(0, sb.length());
        channel = (SocketChannel) selectionKey.channel();
        byteBuffer.clear();
        int len;
        while ((len = channel.read(byteBuffer)) > 0) {
            byteBuffer.flip();
            bytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(bytes);
            sb.append(new String(bytes, StandardCharsets.UTF_8));
            byteBuffer.clear();
        }
        System.out.println(sb.toString());
        if (len < 0) {
            selectionKey.cancel();
            socketChannel.close();
            System.out.println("与服务器失去连接...");
            return false;
        }
        return true;
    }

}
