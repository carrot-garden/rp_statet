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

package de.walware.statet.r.internal.sweave.model;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IProgressMonitor;

import de.walware.ecommons.ltk.GenericResourceSourceUnit;
import de.walware.ecommons.ltk.IModelManager;

import de.walware.statet.r.core.IRCoreAccess;
import de.walware.statet.r.core.RCore;
import de.walware.statet.r.core.RProject;
import de.walware.statet.r.core.model.IRWorkspaceSourceUnit;
import de.walware.statet.r.core.renv.IREnv;
import de.walware.statet.r.sweave.Sweave;


public class RweaveTexDocUnit extends GenericResourceSourceUnit 
		implements IRWorkspaceSourceUnit {
	
	
	public RweaveTexDocUnit(final String id, final IFile file) {
		super(id, file);
	}
	
	
	public String getModelTypeId() {
		return Sweave.R_TEX_MODEL_TYPE_ID;
	}
	
	
	public IRCoreAccess getRCoreAccess() {
		final RProject project = RProject.getRProject(getResource().getProject());
		if (project != null) {
			return project;
		}
		return RCore.getWorkbenchAccess();
	}
	
	public IREnv getREnv() {
		return RCore.getREnvManager().getDefault();
	}
	
	@Override
	public Object getAdapter(final Class required) {
		if (required.equals(IRCoreAccess.class)) {
			return getRCoreAccess();
		}
		return super.getAdapter(required);
	}
	
	
	@Override
	protected void register() {
		super.register();
		final IModelManager rManager = RCore.getRModelManager();
		if (rManager != null) {
			rManager.deregisterDependentUnit(this);
		}
	}
	
	@Override
	protected void unregister() {
		final IModelManager rManager = RCore.getRModelManager();
		if (rManager != null) {
			rManager.deregisterDependentUnit(this);
		}
		super.unregister();
	}
	
	public void reconcileRModel(final int reconcileLevel, final IProgressMonitor monitor) {
	}
	
}
