import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.Arrays;

public class HttpServer {
    int port;
    String directory;
    ServerSocket serverSocket = null;

    public HttpServer(String directory) {
        this(4221, directory);
    }

    public HttpServer(int port, String directory){
        this.port = port;
        this.directory = directory;

        System.out.printf("Starting server at port: %d and directory: %s\n", port, directory);

        try {
            this.serverSocket = new ServerSocket(port);
            serverSocket.setReuseAddress(true);
        } catch (IOException e) {
            System.err.println("IOException: " + e.getMessage());
        }
    }

    public void start() throws IOException {

            while (true) {

                Socket clientSocket;

                try {
                    // this will block until a new connection is received
                    clientSocket = serverSocket.accept();
                } catch (IOException e) {
                    System.err.println("clientSocket IOException: " + e.getMessage());
                    return;
                }

                new Thread(() -> {
                    try (
                            InputStream clientInputStream = clientSocket.getInputStream();
                            OutputStream clientOutputStream = clientSocket.getOutputStream()
                    ) {

                        byte[] readBuffer = new byte[2 << 15];
                        int iBytesRead = clientInputStream.read(readBuffer);
                        readBuffer = Arrays.copyOf(readBuffer, iBytesRead);

                        System.out.println("Amount of bytes received: " + iBytesRead);

                        HttpRequest httpReq = new HttpRequest(readBuffer);
                        String path = httpReq.path;

                        HeaderSet headers = new HeaderSet();
                        byte[] response = createResponse(200, null, null);

                        // TODO: implement routing logic using switch/case
                        if (path.startsWith("/echo/")) {
                            String randomString = path.substring("/echo/".length());
                            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                            byteArrayOutputStream.write(randomString.getBytes());

                            headers.put("Content-Type", "text/plain");

                            response = createResponse(200, byteArrayOutputStream.toByteArray(), headers);
                        } else if (path.equals("/user-agent")) {
                            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                            String userAgent = httpReq.headers.get("User-Agent");

                            if (userAgent == null) {
                                System.err.println("user-agent");
                            }

                            assert userAgent != null;
                            byteArrayOutputStream.write(userAgent.getBytes());

                            headers.put("Content-Type", "text/plain");

                            response = createResponse(200, byteArrayOutputStream.toByteArray(), headers);
                        } else if (path.startsWith("/files/") && this.directory != null) {

                            String filename = path.substring("/files/".length());
                            System.out.println("Filename: " + filename);

                            Path requestedFile = Paths.get(this.directory).resolve(filename).normalize();
                            System.out.println("Requested file: " + requestedFile);

                            // prevent path traversal attacks
                            if (!requestedFile.startsWith(this.directory)) {
                                response = createResponse(500, null, null);
                                clientOutputStream.write(response);
                                clientOutputStream.flush();
                                return;
                            }

                            // process contents
                            if (httpReq.method.equals("POST")) {
                                boolean newFileCreated = requestedFile.toFile().createNewFile();

                                assert newFileCreated;

                                if(httpReq.body != null) {
                                    System.out.printf("Writing %d of bytes\n", httpReq.body.length);
                                    Files.write(requestedFile, httpReq.body);
                                    response = createResponse(201, null, headers);
                                }
                            } else  if (httpReq.method.equals("GET")) {
                                // read file
                                if (!requestedFile.toFile().exists()) {
                                    response = createResponse(404, null, null);
                                    clientOutputStream.write(response);
                                    clientOutputStream.flush();
                                    return;
                                }

                                DataInputStream reader = new DataInputStream(new FileInputStream(requestedFile.toFile()));
                                int nBytesToRead = reader.available();
                                if(nBytesToRead > 0) {
                                    byte[] bytes = new byte[nBytesToRead];
                                    reader.read(bytes);
                                    headers.put("Content-Type", "application/octet-stream");
                                    response = createResponse(200, bytes, headers);
                                }
                            }

                        } else if (!path.equals("/")) {
                            response = createResponse(404, null, null);
                        }

                        clientOutputStream.write(response);
                        clientOutputStream.flush();
                    } catch (IOException e) {
                        System.err.println("IOException: " + e.getMessage());
                    }
                }).start();
            }
    }
    private byte[] createResponse(int statusCode, byte[] body, HeaderSet extraHeaders) {
        ByteArrayOutputStream bOut = new ByteArrayOutputStream(1024);

        // prepare statusline
        switch (statusCode) {
        case 200: {
            byte[] response = "HTTP/1.1 200 OK\r\n".getBytes(StandardCharsets.UTF_8);
            bOut.writeBytes(response);
            break;
        }

        case 201: {
            byte[] response = "HTTP/1.1 201 Created\r\n".getBytes(StandardCharsets.UTF_8);
            bOut.writeBytes(response);
            break;
        }

        case 404: {
            byte[] response = "HTTP/1.1 404 Not Found\r\n".getBytes(StandardCharsets.UTF_8);
            bOut.writeBytes(response);
            break;
        }

        case 500: {
            byte[] response = "HTTP/1.1 500 Internal Server Error\r\n".getBytes(StandardCharsets.UTF_8);
            bOut.writeBytes(response);
            break;
        }

        default:
            break;
        }

        // prepare content-length
        if (body == null) {
            body = new byte[0];
        }

        byte[] contentLength = String.format("%s: %d\r\n", "Content-Length", body.length).getBytes(StandardCharsets.UTF_8);
        bOut.writeBytes(contentLength);


        // prepare HTTP headers
        if (extraHeaders != null) {
            extraHeaders.forEach((key, value) -> {
                String hdr = String.format("%s: %s\r\n", key, value);
                bOut.writeBytes(hdr.getBytes(StandardCharsets.UTF_8));
            });
        }

        // end head part
        // start body
        bOut.writeBytes("\r\n".getBytes());
        bOut.writeBytes(body);

        // return HttpResponse
        return bOut.toByteArray();
    }
}
