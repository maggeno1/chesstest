package de.mschanzer.chesstest.chesstest; // Oder de.mschanzer.chesstest.chesstest.config

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Wrapper;
import java.util.logging.Logger; // Import für Logger hinzufügen

public class ReadOnlyAwareDataSource implements DataSource, Wrapper {

    private static final Logger logger = Logger.getLogger(ReadOnlyAwareDataSource.class.getName());
    private final DataSource targetDataSource;

    public ReadOnlyAwareDataSource(DataSource targetDataSource) {
        this.targetDataSource = targetDataSource;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return new ReadOnlyAwareConnection(targetDataSource.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return new ReadOnlyAwareConnection(targetDataSource.getConnection(username, password));
    }

    // --- Delegieren der restlichen DataSource-Methoden ---
    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        return targetDataSource.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this) || targetDataSource.isWrapperFor(iface);
    }

    @Override
    public java.io.PrintWriter getLogWriter() throws SQLException {
        return targetDataSource.getLogWriter();
    }

    @Override
    public void setLogWriter(java.io.PrintWriter out) throws SQLException {
        targetDataSource.setLogWriter(out);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return targetDataSource.getLoginTimeout();
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        targetDataSource.setLoginTimeout(seconds);
    }

    // Java 7+ method
    public Logger getParentLogger() throws java.sql.SQLFeatureNotSupportedException {
        return targetDataSource.getParentLogger();
    }
}