/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.server.definition.extension;

import com.redhat.devtools.lsp4ij.dap.definitions.extension.DebugAdapterServerExtensionPointBean;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.org.objectweb.asm.AnnotationVisitor;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassVisitor;
import org.jetbrains.org.objectweb.asm.FieldVisitor;
import org.jetbrains.org.objectweb.asm.Opcodes;
import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.TypePath;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code nameKey} and {@code descriptionKey} attributes of the {@code server} and {@code debugAdapterServer}
 * extensions name a key of a resource bundle, not a text shown to the user, so DevKit must not check their
 * capitalization (issue #450).
 * <p>
 * DevKit's "Plugin.xml capitalization" inspection checks an extension attribute against the {@link Nls} capitalization
 * of the bean field it binds to. It checks the text of the property for the key attributes of the platform's own beans
 * only, which it knows by name, and the attribute's own text, here the key, for any other bean. It skips a field that
 * is {@link NonNls}. Both annotations are kept in the class file only, which is where DevKit reads them for a plugin
 * that depends on LSP4IJ, so this test reads them from there.
 */
class ExtensionPointBeanKeyAttributesTest {

    private static final String NLS = Type.getDescriptor(Nls.class);
    private static final String NON_NLS = Type.getDescriptor(NonNls.class);

    @Test
    void serverKeyAttributesAreNotLocalizedText() throws IOException {
        assertKeyAttributesAreNonNls(ServerExtensionPointBean.class);
    }

    @Test
    void debugAdapterServerKeyAttributesAreNotLocalizedText() throws IOException {
        assertKeyAttributesAreNonNls(DebugAdapterServerExtensionPointBean.class);
    }

    private static void assertKeyAttributesAreNonNls(Class<?> beanClass) throws IOException {
        Map<String, Set<String>> annotationsByField = readFieldAnnotations(beanClass);
        for (String field : new String[]{"nameKey", "descriptionKey"}) {
            Set<String> annotations = annotationsByField.get(field);
            assertNotNull(annotations, () -> beanClass.getSimpleName() + " has no field " + field);
            assertFalse(annotations.contains(NLS),
                    () -> beanClass.getSimpleName() + "." + field + " is a resource key, but it is @Nls, so DevKit checks the key's capitalization: " + annotations);
            assertTrue(annotations.contains(NON_NLS),
                    () -> beanClass.getSimpleName() + "." + field + " is a resource key, but it is not @NonNls: " + annotations);
        }
    }

    /**
     * Reads the annotations of each field of the given class from its class file, the declaration and the type
     * annotations of every retention.
     *
     * @param beanClass the class whose class file is read.
     * @return the descriptors of the annotations of each field, by field name.
     */
    private static Map<String, Set<String>> readFieldAnnotations(Class<?> beanClass) throws IOException {
        String classFile = beanClass.getSimpleName() + ".class";
        Map<String, Set<String>> annotationsByField = new HashMap<>();
        try (InputStream in = beanClass.getResourceAsStream(classFile)) {
            assertNotNull(in, () -> "The class file " + classFile + " was not found");
            new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                    Set<String> annotations = annotationsByField.computeIfAbsent(name, n -> new HashSet<>());
                    return new FieldVisitor(Opcodes.ASM9) {
                        @Override
                        public AnnotationVisitor visitAnnotation(String annotationDescriptor, boolean visible) {
                            annotations.add(annotationDescriptor);
                            return null;
                        }

                        @Override
                        public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String annotationDescriptor, boolean visible) {
                            annotations.add(annotationDescriptor);
                            return null;
                        }
                    };
                }
            }, ClassReader.SKIP_CODE);
        }
        return annotationsByField;
    }
}
