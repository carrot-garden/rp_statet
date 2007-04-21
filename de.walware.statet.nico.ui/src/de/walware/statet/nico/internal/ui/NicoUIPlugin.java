/*******************************************************************************
 * Copyright (c) 2006 WalWare/StatET-Project (www.walware.de/goto/statet).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Stephan Wahlbrink - initial API and implementation
 *******************************************************************************/

package de.walware.statet.nico.internal.ui;


import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import de.walware.eclipsecommons.ui.util.ImageRegistryUtil;

import de.walware.statet.base.ui.StatetUIServices;
import de.walware.statet.nico.core.NicoCore;
import de.walware.statet.nico.ui.NicoUI;


/**
 * The activator class controls the plug-in life cycle
 */
public class NicoUIPlugin extends AbstractUIPlugin {

	
	public static final int INTERNAL_ERROR = 100;
	
	/** The shared instance. */
	private static NicoUIPlugin gPlugin;
	
	/**
	 * Returns the shared instance.
	 */
	public static NicoUIPlugin getDefault() {
		
		return gPlugin;
	}


	private ToolRegistry fToolRegistry;

	
	/**
	 * The constructor
	 */
	public NicoUIPlugin() {
		
		gPlugin = this;
	}

	@Override
	public void start(BundleContext context) throws Exception {
		
		super.start(context);
		
		fToolRegistry = new ToolRegistry();
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		try {
			if (fToolRegistry != null) {
				fToolRegistry.dispose();
				fToolRegistry = null;
			}
		}
		finally {
			gPlugin = null;
			super.stop(context);
		}
	}

	
	@Override
	protected ImageRegistry createImageRegistry() {

		return StatetUIServices.getSharedImageRegistry();
	}
	
	@Override
	protected void initializeImageRegistry(ImageRegistry reg) {

		ImageRegistryUtil util = new ImageRegistryUtil(this);
		util.register(NicoUI.IMG_LOCTOOL_CANCEL, ImageRegistryUtil.T_LOCTOOL, "cancel.png"); //$NON-NLS-1$
		util.register(NicoUI.IMG_LOCTOOLD_CANCEL, ImageRegistryUtil.T_LOCTOOL_D, "cancel.png"); //$NON-NLS-1$
	}
	
	public ToolRegistry getToolRegistry() {
		
		return fToolRegistry;
	}
	
	
	public static void log(IStatus status) {
		
		getDefault().getLog().log(status);
	}
	
	public static void logError(int code, String message, Throwable e) {
		
		log(new Status(IStatus.ERROR, NicoCore.PLUGIN_ID, code, message, e)); 
	}
	
}
