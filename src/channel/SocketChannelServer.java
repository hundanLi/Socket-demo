package channel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author li
 * @version 1.0
 * @date 2019-05-28 16:57
 **/
public class SocketChannelServer {

    private static ConcurrentHashMap<String, SocketChannel> users = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        String host = "localhost";
        int port = 8080;
        if (args != null && args.length > 0) {
            for (String arg : args) {
                if (arg.startsWith("-host=")) {
                    host = arg.substring("-host=".length());
                } else if (arg.startsWith("-port=")) {
                    port = Integer.parseInt(arg.substring("-port=".length()));
                }
            }
        }
        InetSocketAddress address = new InetSocketAddress(host, port);
        try (ServerSocketChannel serverChannel = ServerSocketChannel.open()) {
            // 启动ServerSocket监听
            serverChannel.configureBlocking(false);
            serverChannel.bind(address);
            Selector selector = Selector.open();
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);

            System.out.println("Server started on port: " + port);

            // 定时处理I/O就绪的通道
            doSelect(selector);

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @SuppressWarnings("InfiniteLoopStatement")
    private static void doSelect(Selector selector) throws IOException {
        Iterator<SelectionKey> selectionKeys;
        while (true) {
            // 500ms扫描一次
            selector.select(500);
            selectionKeys = selector.selectedKeys().iterator();
            while (selectionKeys.hasNext()) {
                SelectionKey selectionKey = selectionKeys.next();
                if (selectionKey.isAcceptable()) {
                    // 接收客户端连接请求
                    ServerSocketChannel channel = (ServerSocketChannel) selectionKey.channel();
                    SocketChannel socketChannel = channel.accept();
                    socketChannel.configureBlocking(false);
                    socketChannel.register(selector, SelectionKey.OP_READ);

                } else if (selectionKey.isReadable()) {
                    SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
                    String msg = readFromChannel(selectionKey, socketChannel);

                    if (msg.startsWith("login->")) {
                        // 初始登录，保存状态
                        String username = msg.substring("login->".length());
                        if (!users.containsKey(username)) {
                            users.put(username, socketChannel);
                            String echo = "Hello, " + username + ". If you want to exit, enter 'bye'";
                            writeToChannel(echo, socketChannel);
                        } else {
                            String echo = "Sorry, " + username + " has been used. Please try another.";
                            writeToChannel(echo, socketChannel);
                            socketChannel.close();
                            selectionKey.cancel();
                        }

                    } else {
                        // 消息格式： "[user@userId]$ ..."
                        if (msg.length() < "[user@]$ ".length()) {
                            continue;
                        }
                        // 群发消息
                        writeToChannel(msg, null);
                        // 接收到bye，断开连接
                        if (Objects.equals(msg.substring(msg.indexOf("$") + 2), "bye")) {
                            selectionKey.cancel();
                            socketChannel.close();
                            String username = msg.substring(msg.indexOf("@") + 1, msg.indexOf("]"));
                            users.remove(username);
                        }
                    }

                }
                selectionKeys.remove();
                selector.selectedKeys().clear();
            }
        }
    }

    private static String readFromChannel(SelectionKey selectionKey, SocketChannel socketChannel) throws IOException {
        //创建byteBuffer，并开辟一个1k的缓冲区
        ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
        //读取请求码流，返回读取到的字节数
        int readBytes;

        StringBuilder sb = new StringBuilder();
        //读取到字节，对字节进行编解码
        while ((readBytes = socketChannel.read(byteBuffer)) > 0) {
            //将缓冲区从写模式切换到读模式
            byteBuffer.flip();
            //根据缓冲区可读字节数创建字节数组
            byte[] bytes = new byte[byteBuffer.remaining()];
            //向缓冲区读数据到字节数组
            byteBuffer.get(bytes);
            sb.append(new String(bytes, StandardCharsets.UTF_8));
            byteBuffer.clear();
        }
        //判断客户端是否异常断开
        if (readBytes < 0) {
            selectionKey.cancel();
            socketChannel.close();
            // 移除用户在线状态
            for (String username : users.keySet()) {
                if (users.get(username) == socketChannel) {
                    users.remove(username);
                    break;
                }
            }
        }
        return sb.toString();

    }

    private static void writeToChannel(String msg, SocketChannel channel) throws IOException {

        ByteBuffer buffer = ByteBuffer.allocate(msg.getBytes().length);
        buffer.put(msg.getBytes(StandardCharsets.UTF_8));
        if (channel != null) {
            buffer.flip();
            try {
                channel.write(buffer);
            } catch (Exception e) {
                channel.close();
            }
            return;
        }
        for (SocketChannel socketChannel : users.values()) {
            buffer.flip();
            try {
                socketChannel.write(buffer);
            } catch (Exception e) {
                socketChannel.close();
            }
        }

    }
}
