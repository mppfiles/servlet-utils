package ar.mppfiles.utils.servlet;

import java.io.IOException;
import java.sql.SQLException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import org.javalite.activejdbc.Base;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DBConnectionServletFilter implements Filter {

    private final Logger logger = LoggerFactory.getLogger(getClass().getSimpleName());

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        try {
            openDB();
            chain.doFilter(request, response);
        } catch (Exception ex) {
            rollbackDB();
            throw ex;
        } finally {
            closeDB();
        }
    }

    @Override
    public void destroy() {
        closeDB();
    }

    private void openDB() throws ServletException {
        try {
            if (Base.hasConnection() && Base.connection().isClosed()) {
                Base.detach();
            }

            if (!Base.hasConnection()) {
                Base.open(TomcatUtils.getDataSource());
            }
        } catch (SQLException ex) {
            throw new ServletException(ex);
        }
    }

    private void rollbackDB() {
        try {
            if (Base.hasConnection() && !Base.connection().isClosed() && false == Base.connection().getAutoCommit()) {
                Base.rollbackTransaction();
            }
        } catch (SQLException ignore) {
            logger.error("No se pudo revertir la transacción de la BD: " + ignore.getMessage(), ignore);
        }
    }

    private void closeDB() {
        try {
            if (Base.hasConnection() && !Base.connection().isClosed()) {
                Base.close(true);
            }
        } catch (SQLException ignore) {
            logger.error("No se pudo cerrar la conexión a la BD: " + ignore.getMessage(), ignore);
            // ignore
        }
    }
}
