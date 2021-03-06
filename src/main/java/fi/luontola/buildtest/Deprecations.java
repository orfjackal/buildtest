// Copyright © 2011-2013 Esko Luontola <www.orfjackal.net>
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

package fi.luontola.buildtest;

import com.google.common.base.*;
import com.google.common.collect.Lists;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.text.*;
import java.util.*;

public class Deprecations {

    static final String UNEXPECTED_DEPRECATIONS_MESSAGE = "There were unexpected deprecations";
    static final String MISSING_DEPRECATIONS_MESSAGE = "Expected some entities to be deprecated by they were not or have been removed";
    static final String EXPIRED_DEPRECATIONS_MESSAGE = "It is now time to remove the following deprecated entities";

    private static final Type DEPRECATED = Type.getType(Deprecated.class);

    private final List<Deprecation> expected = new ArrayList<Deprecation>();

    public Deprecations add(String deprecation) {
        return add(deprecation, formatDate(new Date()), 1);
    }

    public Deprecations add(String deprecation, String deprecationDate, int transitionPeriodInDays) {
        Date dateOfRemoval = plusDays(parseDate(deprecationDate), transitionPeriodInDays + 1);
        expected.add(new Deprecation(deprecation, dateOfRemoval));
        return this;
    }

    public void verify(Iterable<ClassNode> classes) {
        verify(classes, new Date());
    }

    public void verify(Iterable<ClassNode> classes, Date now) {
        List<String> expected = Lists.transform(this.expected, Deprecation.GetIdentifier);
        List<String> actual = findDeprecations(classes);

        List<String> unexpected = new ArrayList<String>();
        unexpected.addAll(actual);
        unexpected.removeAll(expected);
        assertEmpty(UNEXPECTED_DEPRECATIONS_MESSAGE, unexpected);

        List<String> missing = new ArrayList<String>();
        missing.addAll(expected);
        missing.removeAll(actual);
        assertEmpty(MISSING_DEPRECATIONS_MESSAGE, missing);

        List<String> expired = new ArrayList<String>();
        for (Deprecation deprecation : this.expected) {
            if (deprecation.isExpired(now)) {
                expired.add(deprecation.identifier);
            }
        }
        assertEmpty(EXPIRED_DEPRECATIONS_MESSAGE, expired);
    }

    private List<String> findDeprecations(Iterable<ClassNode> classes) {
        List<String> deprecations = new ArrayList<String>();
        for (ClassNode clazz : classes) {
            if (isDeprecated(clazz)) {
                deprecations.add(format(clazz));
            }
            for (MethodNode method : clazz.methods) {
                if (isDeprecated(method)) {
                    deprecations.add(format(clazz, method));
                }
            }
            for (FieldNode field : clazz.fields) {
                if (isDeprecated(field)) {
                    deprecations.add(format(clazz, field));
                }
            }
        }
        return deprecations;
    }

    private static boolean isDeprecated(ClassNode clazz) {
        return containsDeprecated(clazz.visibleAnnotations);
    }

    private boolean isDeprecated(MethodNode method) {
        return containsDeprecated(method.visibleAnnotations);
    }

    private boolean isDeprecated(FieldNode field) {
        return containsDeprecated(field.visibleAnnotations);
    }

    private static boolean containsDeprecated(List<AnnotationNode> annotations) {
        for (AnnotationNode annotation : nonNull(annotations)) {
            if (annotation.desc.equals(DEPRECATED.getDescriptor())) {
                return true;
            }
        }
        return false;
    }

    private static <T> List<T> nonNull(List<T> list) {
        if (list == null) {
            return Collections.emptyList();
        }
        return list;
    }


    // pretty printing

    private static String format(ClassNode clazz) {
        return Type.getObjectType(clazz.name).getClassName();
    }

    private static String format(ClassNode clazz, MethodNode method) {
        StringBuilder sb = new StringBuilder();
        sb.append(format(clazz));
        sb.append("#");
        sb.append(method.name);
        sb.append("(");
        Type[] args = Type.getMethodType(method.desc).getArgumentTypes();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(args[i].getClassName());
        }
        sb.append(")");
        return sb.toString();
    }

    private String format(ClassNode clazz, FieldNode field) {
        return format(clazz) + "#" + field.name;
    }

    private static void assertEmpty(String message, List<String> actual) {
        if (!actual.isEmpty()) {
            throw new AssertionError(message + ":\n" + format(actual));
        }
    }

    private static String format(List<String> deprecations) {
        String prefix = "- \"";
        String suffix = "\"\n";
        return prefix + Joiner.on(suffix + prefix).join(deprecations) + suffix;
    }


    // date handling

    private static Date plusDays(Date date, int days) {
        Calendar tmp = Calendar.getInstance();
        tmp.setTime(date);
        tmp.add(Calendar.DAY_OF_MONTH, days);
        return tmp.getTime();
    }

    private static String formatDate(Date date) {
        return isoDate().format(date);
    }

    private static Date parseDate(String date) {
        try {
            return isoDate().parse(date);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    private static SimpleDateFormat isoDate() {
        return new SimpleDateFormat("yyyy-MM-dd");
    }


    private static class Deprecation {
        public static final Function<Deprecation, String> GetIdentifier = new Function<Deprecation, String>() {
            @Override
            public String apply(Deprecation input) {
                return input.identifier;
            }
        };

        private final String identifier;
        private final Date dateOfRemoval;

        private Deprecation(String identifier, Date dateOfRemoval) {
            this.dateOfRemoval = dateOfRemoval;
            this.identifier = identifier;
        }

        public boolean isExpired(Date now) {
            return now.after(dateOfRemoval);
        }

        @Override
        public String toString() {
            return identifier;
        }
    }
}
