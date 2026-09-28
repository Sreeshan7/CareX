package com.carex.leave.common.error;

import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;

/** Extracts the violated PostgreSQL constraint name from a (wrapped) exception. */
public final class ConstraintNames {
    private ConstraintNames() {}

    public static String extract(Throwable ex) {
        Throwable t = ex;
        while (t != null) {
            if (t instanceof PSQLException psql) {
                ServerErrorMessage msg = psql.getServerErrorMessage();
                if (msg != null && msg.getConstraint() != null) {
                    return msg.getConstraint();
                }
            }
            if (t instanceof org.hibernate.exception.ConstraintViolationException cve && cve.getConstraintName() != null) {
                return cve.getConstraintName();
            }
            t = t.getCause();
        }
        return null;
    }

    public static boolean is(Throwable ex, String name) {
        return name.equals(extract(ex));
    }
}
