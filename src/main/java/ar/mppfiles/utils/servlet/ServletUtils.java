package ar.mppfiles.utils.servlet;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.javalite.common.JsonHelper;
import org.javalite.common.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utilitarios para trabajar con Servlets.
 * @author mppfiles + código "prestado" de <a href="https://javalite.io/activeweb">Javalite Activeweb</a>
 */
public class ServletUtils {

    private final Logger logger = LoggerFactory.getLogger(getClass().getSimpleName());

    protected final HttpServletRequest request;
    protected final HttpServletResponse response;
    protected final Pattern regExIdPattern = Pattern.compile("/([0-9]+)");
    private final String BASE_JSP_PATH = "/WEB-INF/jsp";

    private Integer id;

    public ServletUtils(HttpServletRequest request, HttpServletResponse response) {
        this.request = request;
        this.response = response;

        this.fillIdFromUri(request);
    }

    /**
     * Obtiene una referencia al objeto HttpServletRequest.
     *
     * @return objeto HttpServletRequest.
     */
    public HttpServletRequest getRequest() {
        return request;
    }

    /**
     * Obtiene una referencia al objeto HttpServletResponse.
     *
     * @return objeto HttpServletResponse.
     */
    public HttpServletResponse getResponse() {
        return response;
    }

    /**
     * Devuelve el parámetro 'id', si está presente en la URL.
     *
     * @return ID del request.
     */
    public Integer getId() {
        return this.id;
    }

    /**
     * Obtiene la URL absoluta de la app desplegada en el contenedor actual.
     * Protocolo + host + port (si utiliza). 
     * Ej. para la URL:
     * https://example.com:8082/webapp/modulo/accion?query=123 
     * 
     * devuelve: https://example.com:8082
     *
     * @return URL absoluta, hasta antes del ContextPath.
     */
    public String getBaseUrl() {
        StringBuffer url = request.getRequestURL();
        String uri = request.getRequestURI();
        String base = url.substring(0, url.length() - uri.length());

        return base;
    }

    /**
     * Devuelve true si es un request Ajax.
     *
     * @return true si el request es Ajax.
     */
    public boolean isXhr() {
        String xhr = request.getHeader("X-Requested-With");
        if (xhr == null) {
            xhr = request.getHeader("x-requested-with");
        }
        return xhr != null && xhr.toLowerCase().equals("xmlhttprequest");
    }

    /**
     * Devuelve el nombre de usuario autenticado desde el contenedor.
     *
     * @return
     */
    public String getUsuario() {
        return request.getRemoteUser();
    }

    /**
     * Devuelve los parámetros del request (GET/POST) como Map. Si un parámetro
     * viene repetido, sólo devuelve el primero de ellos.
     *
     * @return
     */
    public Map<String, String> params1st() {
        Map<String, String> params = new HashMap<>();
        Enumeration names = request.getParameterNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement().toString();
            params.put(name, request.getParameter(name));
        }

        //agrego ID como parámetro, si está presente en la URL.
        if (getId() != null) {
            params.put("id", this.getId().toString());
        }

        return params;
    }

    /**
     * Devuelve al cliente una respuesta desde un JSP.
     *
     * @see #mergeJsp(java.lang.String)
     * @param path
     * @throws ServletException
     * @throws IOException
     */
    public void renderJsp(String path) throws ServletException, IOException {
        response.getWriter().write(mergeJsp(path));
    }

    /**
     * Construye un JSP y lo devuelve como String.
     *
     * @param path nombre de archivo JSP, incluyendo la extensión. Relativo a
     * /WEB-INF/jsp. Ejemplo: /modulo/accion.jsp
     * @return
     * @throws ServletException
     * @throws IOException
     */
    public String mergeJsp(String path) throws ServletException, IOException {
        path = (path.charAt(0) == '/') ? path : "/" + path;

        CharArrayWriterResponse jspResponse = new CharArrayWriterResponse(response);
        try {
            request.getRequestDispatcher(BASE_JSP_PATH + path).forward(request, jspResponse);
        } catch (IOException | ServletException ex) {
            logger.error("Error", ex);
            throw ex;
        }

        return jspResponse.getOutput();
    }

    /**
     * Devuelve al cliente una respuesta JSON 200 (OK).
     *
     * @param result Objeto a serializar como JSON
     * @throws IOException
     */
    public void renderJson(Object result) throws IOException {
        renderJson(result, HttpServletResponse.SC_OK);
    }

    /**
     * Devuelve al cliente una respuesta JSON con un código de estado HTTP
     * puntual.
     *
     * @param result Objeto a serializar como JSON
     * @param sc código de estado (200, 401, etc)
     * @throws IOException
     */
    public void renderJson(Object result, int sc) throws IOException {
        String json = JsonHelper.toJsonString(result);

        //mando los headers, etc.
        response.setStatus(sc);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");

        response.getWriter().write(json);
    }

    /**
     * Devuelve al cliente una respuesta de tipo "archivo descargable".
     *
     * @param archivo objeto File para descarga
     * @param nombre_descarga nombre con el que descarga el archivo
     * @param borrar si se eliminará el archivo luego de la descarga.
     * @throws IOException
     */
    public void renderFileDownload(File archivo, String nombre_descarga, boolean borrar) throws IOException {
        response.setHeader("Content-Disposition", "attachment; filename=\"" + nombre_descarga + "\"");
        renderFile(archivo, borrar);
    }

    /**
     * Devuelve al cliente una respuesta de tipo "archivo" (ej. binario).
     *
     * @param archivo
     * @param borrar
     * @throws IOException
     */
    public void renderFile(File archivo, boolean borrar) throws IOException {
        try {
            stream(new FileInputStream(archivo), response.getOutputStream());
        } finally {
            if (borrar) {
                Files.delete(archivo.toPath());
            }
        }
    }

    /**
     * Obtiene el body del request como una lista de objetos JSON.
     *
     * @return Java List con los datos JSON.
     * @throws IOException
     * @throws ServletException
     */
    public List jsonList() throws IOException, ServletException {
        checkJsonContentType();
        return JsonHelper.toList(Util.read(request.getInputStream()));
    }

    /**
     * Obtiene el body del request como un JSON map.
     *
     * @return Java Map con los datos JSON.
     * @throws javax.servlet.ServletException
     * @throws java.io.IOException
     */
    public Map jsonMap() throws ServletException, IOException {
        checkJsonContentType();
        return JsonHelper.toMap(Util.read(request.getInputStream()));
    }

    /**
     * Obtiene el body del request como un arreglo de JSON map.
     *
     * @return arreglo de Java Maps con los datos JSON.
     * @throws javax.servlet.ServletException
     * @throws java.io.IOException
     */
    public Map[] jsonMaps() throws ServletException, IOException {
        checkJsonContentType();
        return JsonHelper.toMaps(Util.read(request.getInputStream()));
    }

    /**
     * Comprueba que el request es de tipo JSON.
     *
     * @throws ServletException
     */
    private void checkJsonContentType() throws ServletException {
        if (!(request.getHeader("Content-Type") != null && request.getHeader("Content-Type").toLowerCase().contains("application/json"))) {
            throw new ServletException("El Content-Type de este request debe ser application/json, se recibió: " + request.getHeader("Content-Type"));
        }
    }

    /**
     * Loguea un error usando el mecanismo de logueo.
     *
     * @param e excepción para loguear.
     */
    protected final void logError(Exception e) {
        logger.error("Error", e);
    }

    /**
     * Transfiere datos de un InputStream a un OutputStream.
     *
     * @param in
     * @param out
     * @throws IOException
     */
    protected final void stream(InputStream in, OutputStream out) throws IOException {
        byte[] bytes = new byte[1024];
        int x;
        while ((x = in.read(bytes)) != -1) {
            out.write(bytes, 0, x);
        }
        in.close();
    }

    /**
     * Rellena el parámetro "id" que viene de la URL. La URL tiene que terminar
     * con /(numero). Ej. /clientes/autorizar/33 => 33
     *
     * @param request
     */
    private void fillIdFromUri(HttpServletRequest request) {
        String pathInfo = request.getPathInfo();

        if (pathInfo == null || "/".equals(pathInfo)) {
            return;
        }

        // fill "id" from request (pathInfo), if any
        Matcher matcher = regExIdPattern.matcher(request.getPathInfo());

        if (matcher.find()) {
            id = Integer.parseInt(matcher.group(1));
        }
    }
}
