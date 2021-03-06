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

package de.walware.statet.r.nico.impl;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import de.walware.ecommons.ts.ITool;
import de.walware.ecommons.ts.IToolRunnable;
import de.walware.ecommons.ts.IToolService;

import de.walware.statet.r.console.core.IRBasicAdapter;
import de.walware.statet.r.internal.console.core.RConsoleCorePlugin;
import de.walware.statet.r.internal.nico.RNicoMessages;


/**
 * Cancel support for windows/rterm.
 */
class RTermCancelRunnable implements IToolRunnable {
	
	
	RTermCancelRunnable() {
	}
	
	
	public String getLabel() {
		return RNicoMessages.RTerm_CancelTask_label;
	}
	
	public String getTypeId() {
		return null; // not a real runnable
	}
	
	public boolean changed(final int event, final ITool process) {
		return true;
	}
	
	public boolean isRunnableIn(final ITool tool) {
		return true;
	}
	
	public void run(final IToolService service,
			final IProgressMonitor monitor) throws CoreException {
		final IRBasicAdapter r = (IRBasicAdapter) service;
		final URL dir = RConsoleCorePlugin.getDefault().getBundle().getEntry("/win32/SendSignal.exe"); //$NON-NLS-1$
		try {
			monitor.beginTask(RNicoMessages.RTerm_CancelTask_SendSignal_label, 10);
			final String local = FileLocator.toFileURL(dir).getPath();
			final File file = new File(local);
			if (!file.exists()) {
				throw new IOException("Missing File '"+file.getAbsolutePath() + "'."); //$NON-NLS-1$ //$NON-NLS-2$
			}
			monitor.worked(1);
			final RTermController controller = (RTermController) r.getController();
			final Long processId = controller.fProcessId;
			if (processId == null) {
				RConsoleCorePlugin.log(new Status(IStatus.WARNING, RConsoleCorePlugin.PLUGIN_ID,
						"Cannot run cancel command: process-id of Rterm process is missing." )); //$NON-NLS-1$
				return;
			}
			final String[] cmd = new String[] {
					file.getAbsolutePath(), processId.toString()
					// the tool usually does not print output
			};
			final Process process = Runtime.getRuntime().exec(cmd);
			monitor.worked(1);
			while (true) {
				try {
					final int code = process.exitValue();
					if (code != 0) {
						final StringBuffer detail = new StringBuffer();
						detail.append("\n command = "); //$NON-NLS-1$
						detail.append(Arrays.toString(cmd));
						detail.append("\n os.name = "); //$NON-NLS-1$
						detail.append(System.getProperty("os.name")); //$NON-NLS-1$
						detail.append("\n os.version = "); //$NON-NLS-1$
						detail.append(System.getProperty("os.version")); //$NON-NLS-1$
						detail.append("\n os.arch = "); //$NON-NLS-1$
						detail.append(System.getProperty("os.arch")); //$NON-NLS-1$
						throw new IOException("Command failed: " + detail); //$NON-NLS-1$
					}
					break;
				}
				catch (final IllegalThreadStateException e) {
				}
				if (monitor.isCanceled()) {
					process.destroy();
					RConsoleCorePlugin.log(new Status(IStatus.WARNING, RConsoleCorePlugin.PLUGIN_ID, -1,
							"Sending CTRL+C to R process canceled, command: " + Arrays.toString(cmd), null )); //$NON-NLS-1$
					break;
				}
				try {
					Thread.sleep(50);
				}
				catch (final InterruptedException e) {
					// continue directly
				}
			}
		}
		catch (final IOException e) {
			throw new CoreException(new Status(IStatus.WARNING, RConsoleCorePlugin.PLUGIN_ID, -1,
					"Error Sending CTRL+C to R process.", e) ); //$NON-NLS-1$
		}
		finally {
			monitor.done();
		}
	}
	
}
