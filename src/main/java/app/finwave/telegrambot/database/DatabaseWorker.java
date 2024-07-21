package app.finwave.telegrambot.database;

import app.finwave.telegrambot.config.ConfigWorker;
import app.finwave.telegrambot.config.DatabaseConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.DriverManager;

@Singleton
public class DatabaseWorker {
    protected static final Logger log = LoggerFactory.getLogger(DatabaseWorker.class);

    protected Connection connection;
    protected DSLContext context;

    @Inject
    public DatabaseWorker(ConfigWorker configWorker, Migrator migrator) {
        DatabaseConfig config = configWorker.database;

        log.info("Init database...");

        System.setProperty("org.jooq.no-logo", "true");

        try {
            migrator.migrate();
        } catch (Exception e) {
            log.error("Error to migrate", e);

            System.exit(1);
        }

        log.info("Connect to database...");

        try {
            connection = DriverManager.getConnection(config.url, config.user, config.password);

            context = DSL.using(connection, SQLDialect.POSTGRES);
        } catch (Exception e) {
            log.error("Error to connect", e);

            System.exit(1);
        }
    }

    public DSLContext getDefaultContext() {
        return context;
    }

    public <T extends AbstractDatabase> T get(Class<T> tClass, DSLContext context) {
        try {
            return tClass.getConstructor(DSLContext.class).newInstance(context);
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public <T extends AbstractDatabase> T get(Class<T> tClass) {
        return get(tClass, context);
    }
}
