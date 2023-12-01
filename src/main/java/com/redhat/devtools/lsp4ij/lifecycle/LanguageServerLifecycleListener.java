/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.lifecycle;

import com.redhat.devtools.lsp4ij.LanguageServerWrapper;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.jsonrpc.messages.Message;

/**
 * Language server lifecycle listener
 *
 * @author Angelo ZERR
 */
public interface LanguageServerLifecycleListener {
    
    void handleStatusChanged(LanguageServerWrapper languageServer);

    void handleLSPMessage(Message message, MessageConsumer consumer, LanguageServerWrapper languageServer);

    void handleError(LanguageServerWrapper languageServer, Throwable exception);

    void dispose();

}
