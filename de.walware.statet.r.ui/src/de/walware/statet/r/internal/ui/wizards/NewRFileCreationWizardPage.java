/*******************************************************************************
 * Copyright (c) 2005-2011 WalWare/StatET-Project (www.walware.de/goto/statet).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Stephan Wahlbrink - initial API and implementation
 *******************************************************************************/

package de.walware.statet.r.internal.ui.wizards;

import org.eclipse.jface.viewers.IStructuredSelection;

import de.walware.ecommons.ui.dialogs.groups.Layouter;

import de.walware.statet.ext.ui.wizards.NewElementWizardPage;


/**
 * The "New" wizard page allows setting the container for
 * the new file as well as the file name. The page
 * will only accept file name without the extension or
 * with the extension that matches the expected one (r).
 */
public class NewRFileCreationWizardPage extends NewElementWizardPage {
	
	
	private static final String fgDefaultExtension = ".R"; //$NON-NLS-1$
	
	
	ResourceGroup fResourceGroup;
	
	/**
	 * Constructor.
	 */
	public NewRFileCreationWizardPage(final IStructuredSelection selection) {
		super("NewRFileCreationWizardPage", selection); //$NON-NLS-1$
		
		setTitle(Messages.NewRScriptFileWizardPage_title);
		setDescription(Messages.NewRScriptFileWizardPage_description);
		
		fResourceGroup = new ResourceGroup(fgDefaultExtension);
	}
	
	
	@Override
	protected void createContents(final Layouter layouter) {
		fResourceGroup.createGroup(layouter);
	}
	
	@Override
	public void setVisible(final boolean visible) {
		super.setVisible(visible);
		if (visible) {
			fResourceGroup.setFocus();
		}
	}
	
	public void saveSettings() {
		fResourceGroup.saveSettings();
	}
	
	@Override
	protected void validatePage() {
		updateStatus(fResourceGroup.validate());
	}
	
}
