import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class HttpRequest implements Serializable {
    protected int statusCode;
    protected String path;
    protected String method;
    protected HeaderSet headers;
    protected byte[] body;
    protected byte[] rawHttpRequest;

    HttpRequest(byte[] httpReq) {
        rawHttpRequest = httpReq;
        statusCode = 0;
        method = extractMethod(httpReq);
        path = extractPath(httpReq);
        headers = parseHeaders(httpReq);
    }

    private String extractMethod(byte[] httpRequest) {
        String request = new String(httpRequest, StandardCharsets.UTF_8);
        return request.split("\r\n")[0].split(" ")[0];
    }

    private String extractPath(byte[] httpRequest) {
        String request = new String(httpRequest, StandardCharsets.UTF_8);
        String requestLine = request.split("\r\n")[0];
        int pathStart = requestLine.indexOf('/');
        int pathEnd = requestLine.substring(pathStart).indexOf(' ');

        return requestLine.substring(pathStart, pathStart + pathEnd);
    }

    private HeaderSet parseHeaders(byte[] httpRequest) {
        String request = new String(httpRequest, StandardCharsets.UTF_8);
        HeaderSet headers = new HeaderSet();

        String[] headers_and_body = request.split("\r\n\r\n");

        if (headers_and_body.length == 2) {
            body = headers_and_body[1].getBytes(StandardCharsets.UTF_8);
            System.out.printf("Body length = %d bytes\n", body.length);
        }

        List<String> hdrLines = List.of(headers_and_body[0].split("\r\n"));
        hdrLines = hdrLines.subList(1, hdrLines.size());
        hdrLines.forEach(x -> {
            String[] kv = x.split(": ");
            headers.put(kv[0], kv[1]);
        });

        return headers;
    }
}
