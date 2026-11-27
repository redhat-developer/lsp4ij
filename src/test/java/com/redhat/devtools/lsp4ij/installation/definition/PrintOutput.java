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
package com.redhat.devtools.lsp4ij.installation.definition;

/**
 * A program which prints its arguments, each on a line: on its standard error those which start with {@code err:},
 * without it, and on its standard output the others. An argument {@code cwd} prints the directory it runs in, as
 * {@code cwd=<directory>}. It exits with the code {@code N} of an argument {@code exit:N}, and with 0 without one.
 */
public final class PrintOutput {

    private PrintOutput() {
    }

    public static void main(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("exit:")) {
                System.exit(Integer.parseInt(arg.substring("exit:".length())));
            } else if (arg.equals("cwd")) {
                System.out.println("cwd=" + System.getProperty("user.dir"));
            } else if (arg.startsWith("err:")) {
                System.err.println(arg.substring("err:".length()));
            } else {
                System.out.println(arg);
            }
        }
    }
}
