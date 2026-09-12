/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */
package co.edu.escuelaing.webapplication.webapplication;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 *
 * @author ander
 */
public class Webapplication {

    private static final String PUBLIC_DIR = "public"; //Debe ser la carpeta que representa los recursos publicos

    public static void main(String[] args) throws IOException {
        ServerSocket serversocket = new ServerSocket(35000); //Se abre el puerto para escuchar en el puerto 35000
        while(true) {
            try (Socket socket = serversocket.accept()){//Aca es lo de acceptar la solicitud, no olvidar que es de tipo socket y por eso se llama socket
            //Tambien de que el Try tenga parametros es que se cierra la conexión automaticamente en caso de que el accept falle. 
            Reader streamReader = new InputStreamReader(socket.getInputStream()); //El InputStream son los bytes crudos del socket; el InputStreamReader es quien los traduce a caracteres
            BufferedReader reader = new BufferedReader(streamReader); //El BufferedReader envuelve al Reader de caracteres para poder leer linea por linea con readLine()

            String line = reader.readLine(); //Se lee la primera linea de la solicitud
            if(line == null){
                continue; //Si la linea esta nula, se continua con la siguiente conexión.
            }
            String[] parts = line.split(" "); //Se divide la linea en partes para obtener el metodo, la ruta y la version del protocolo HTTP
            String path = parts[1]; //Se obtiene la ruta de la solicitud

            String method = parts[0]; //Se obtiene el metodo de la solicitud
            OutputStream out = socket.getOutputStream(); //Se obtiene el OutputStream del socket para poder escribir la respuesta
            if(!method.equals("GET")){
                String body = "<html><body><h1>405 Method Not Allowed</h1></body></html>";
                byte [] bodyBytes = body.getBytes(StandardCharsets.UTF_8);

                String errorMessage = "HTTP/1.1 405 Method Not Allowed\r\n"
                        + "Content-Type: text/html; charset=UTF-8\r\n"
                        + "Content-Length: " + bodyBytes.length + "\r\n"
                        + "\r\n";

                out.write(errorMessage.getBytes(StandardCharsets.UTF_8));
                out.write(bodyBytes);
                out.flush();
                continue;
            }

            if (path.equals("/")){
                path = "/index.html"; //Si la ruta es la raiz, se redirige a index.html
            }
            System.out.println("Solicitud recibida: " + path); //Se imprime la ruta de la solicitud
            System.out.println(line); //Se imprime la primera linea de la solicitud

            // basePath = la carpeta "public" convertida a su direccion absoluta y ya normalizada (sin ".." ni cosas raras)
            Path basePath = Paths.get(PUBLIC_DIR).toAbsolutePath().normalize();
            // newPath = base + lo que pidio el cliente, ya resuelto y simplificado (aqui es donde se "cancelan" los "../")
            Path newPath = basePath.resolve(path.substring(1)).normalize();

            // Si despues de normalizar, la ruta resultante ya NO empieza dentro de basePath,
            // significa que el cliente intento salirse de la carpeta public con algo tipo "../"
            if (!newPath.startsWith(basePath)) {
                String body = "<html><body><h1>403 Forbidden</h1></body></html>";
                byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);

                String errorMessage = "HTTP/1.1 403 Forbidden\r\n"
                        + "Content-Type: text/html; charset=UTF-8\r\n"
                        + "Content-Length: " + bodyBytes.length + "\r\n"
                        + "\r\n";

                out.write(errorMessage.getBytes(StandardCharsets.UTF_8));
                out.write(bodyBytes);
                out.flush();
                continue; //Se rechaza el intento de path traversal y se continua con la siguiente conexion
            }

            if (!(Files.exists(newPath))){

                String body = "<html><body><h1>404 Not Found</h1></body></html>";
                byte [] bodyBytes = body.getBytes(StandardCharsets.UTF_8);

                String errorMessage = "HTTP/1.1 404 Not Found\r\n"
                        + "Content-Type: text/html; charset=UTF-8\r\n"
                        + "Content-Length: " + bodyBytes.length + "\r\n"
                        + "\r\n";

                out.write(errorMessage.getBytes(StandardCharsets.UTF_8));
                out.write(bodyBytes);
                out.flush();
                continue; //Si el archivo no existe, se envia un mensaje de error 404 y se continua con la siguiente conexión
            }

            byte[] fileByteContent = Files.readAllBytes(newPath); //Se leen todos los bytes del archivo solicitado


            int httpBodyLenght = fileByteContent.length;
            String contentType = getContentType(path);

            String headers = "HTTP/1.1 200 OK\r\n"
                    + "Content-Type: " + contentType + "\r\n"
                    + "Content-Length: " + httpBodyLenght + "\r\n"
                    + "\r\n";

            out.write(headers.getBytes(StandardCharsets.UTF_8)); //Se escriben los headers de la respuesta
            out.write(fileByteContent);
            out.flush(); //Se vacia el buffer del OutputStream
            } catch (Exception e){
                System.out.println("Algo ha ocurrido en el sistema");
                e.printStackTrace();
            }
        }
    }

    private static String getContentType(String path){
        String extension = path.substring(path.lastIndexOf('.') + 1);
        switch (extension) {
            case "html":
                return "text/html; charset=UTF-8";
            case "js":
                return "text/javascript; charset=UTF-8";
            case "png":
                return "image/png";
            case "jpeg":
            case "jpg":
                return "image/jpeg";
            default:
                return "application/octet-stream";
        }
    }
}
//System.out.println(System.getProperty("user.dir")); Para probar donde estoy parado y asi verificar que
//este ejecutandose el programa dentro de la carpeta raíz del pom.

