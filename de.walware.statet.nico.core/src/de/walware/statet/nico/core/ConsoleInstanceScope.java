/*******************************************************************************
 * Copyright (c) 2007-2011 WalWare/StatET-Project (www.walware.de/goto/statet).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Stephan Wahlbrink - initial API and implementation
 *******************************************************************************/

package de.walware.statet.nico.core;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IScopeContext;
import org.eclipse.core.runtime.preferences.InstanceScope;


/**
 * Dummy scope to overlay instance preferences with special values for consoles.
 */
public final class ConsoleInstanceScope implements IScopeContext {
	
	
	public static final String SCOPE = "nico.instance"; //$NON-NLS-1$
	
	
	private final InstanceScope fBaseScope;
	
	
	public ConsoleInstanceScope() {
		fBaseScope = new InstanceScope();
	}
	
	public IPath getLocation() {
		return fBaseScope.getLocation();
	}
	
	public String getName() {
		return SCOPE;
	}
	
	public IEclipsePreferences getNode(final String qualifier) {
		final int idx = qualifier.indexOf('/');
		if (idx < 0) {
			return (IEclipsePreferences) fBaseScope
					.getNode(NicoPreferenceNodes.SCOPE_QUALIFIER)
					.node(qualifier);
		}
		else {
			return (IEclipsePreferences) fBaseScope
					.getNode(qualifier.substring(0, idx))
					.node(NicoPreferenceNodes.SCOPE_QUALIFIER)
					.node(qualifier.substring(idx+1));
		}
	}
	
}
