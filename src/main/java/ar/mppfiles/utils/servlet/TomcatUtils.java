package ar.mppfiles.utils.servlet;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

/**
 * Utilitarios para trabajo con el contenedor de apĺicaciones (Tomcat, o el que fuere).
 * @author mppfiles
 */
public class TomcatUtils
{
    private static final String ENTORNO_PRODUCCION = "produccion";
    private static final String DEFAULT_DB_POOL_NAME = "db_pool";
    
    /***
     * Obtiene el valor de una variable de entorno del contenedor (context.xml => server.xml)
     * @param name nombre de la variable a buscar
     * @return el valor declarado como Object (se debe castear)
     */
    public static Object getEnvironmentVariable(String name) {

        try {
            Context context = new InitialContext();
            Context envCtx = (Context) context.lookup("java:comp/env");

            return envCtx.lookup(name);

        } catch (NamingException ex) {
            throw new RuntimeException("No se pudo obtener el valor de la variable de entorno: '" + name+"'", ex);
        }
    }
    
    /***
     * Devuelve el nombre del entorno configurado en el servidor, en caso que esté declarado.
     * @return nombre del entorno.
     */
    public static String getEntorno() {
        String entorno;
        
        try {
            entorno = (String)getEnvironmentVariable("entorno");
        } catch(Exception e) {
            throw new RuntimeException("No se ha definido el entorno en la app (context.xml) y/o el servidor (server.xml)."); 
        }
        return entorno;
    }
    
    /***
     * Detecta si el entorno es producción o no, de acuerdo a la config. del servidor.
     * @return resultado de la comprobación
     */
    public static boolean entornoEsProduccion() {
        String entorno = getEntorno();
        
        return entorno.equalsIgnoreCase(ENTORNO_PRODUCCION);
    }
    
    /**
     * Devuelve el "datasource por defecto" para una aplicación.
     * @return DataSource para realizar las conexiones pertinentes.
     */
    public static DataSource getDataSource() {
        return getDataSource(null);
    }
    
    /**
     * Devuelve el datasource correspondiente al nombre de pool especificado
     * (sin prefijo jdbc/)
     * @param poolName nombre del pool a obtener.
     * @return DataSource para realizar las conexiones pertinentes.
     */
    public static DataSource getDataSource(String poolName) {
        
        if(poolName == null) {
            poolName = DEFAULT_DB_POOL_NAME;
        }
        
        DataSource ds = (DataSource) getEnvironmentVariable("jdbc/" + poolName);

        return ds;
    }
}
