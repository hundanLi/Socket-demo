package file;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Scanner;

/**
 * @author li
 * @version 1.0
 * @date 2019-05-26 19:28
 **/
public class FileClient {

    public static void main(String[] args) throws IOException {
        Socket socket = null;
        FileInputStream fileInputStream = null;
        DataOutputStream dataOutputStream = null;
        Scanner scanner = null;

        String host = "localhost";
        int port = 8080;
        String pathname = "resources/client/text.txt";
        String filename = pathname.substring(pathname.lastIndexOf("/") + 1);
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), 3000);
            scanner = new Scanner(socket.getInputStream());

            fileInputStream = new FileInputStream(new File(pathname));
            dataOutputStream = new DataOutputStream(socket.getOutputStream());
            dataOutputStream.writeUTF(filename);

            byte[] bytes = new byte[1024];
            int len;
            while ((len = fileInputStream.read(bytes)) != -1) {
                dataOutputStream.write(bytes, 0, len);
            }

            System.out.println("completed...");

        } catch (FileNotFoundException e) {
            System.out.println("File Not Exist：　" + e.getMessage());

        } catch (Exception e) {
            System.out.println(e.getMessage());
        }finally {
            if (scanner != null) {
                scanner.close();
            }
            if (dataOutputStream != null) {
                dataOutputStream.close();
            }
            if (fileInputStream != null) {
                fileInputStream.close();
            }
            if (socket != null) {
                socket.close();
            }
        }
    }
}
