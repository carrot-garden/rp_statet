/*******************************************************************************
 * Copyright (c) 2008-2011 WalWare/StatET-Project (www.walware.de/goto/statet).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Stephan Wahlbrink - initial API and implementation
 *******************************************************************************/

package de.walware.statet.r.launching;

import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.variables.IDynamicVariable;
import org.eclipse.core.variables.IStringVariable;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IStatusHandler;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.jface.text.AbstractDocument;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.statushandlers.StatusManager;

import de.walware.ecommons.ltk.ISourceUnit;
import de.walware.ecommons.ltk.IWorkspaceSourceUnit;
import de.walware.ecommons.preferences.PreferencesUtil;
import de.walware.ecommons.text.TextUtil;
import de.walware.ecommons.ts.IToolRunnable;
import de.walware.ecommons.variables.core.DynamicVariable;
import de.walware.ecommons.variables.core.StringVariable;
import de.walware.ecommons.variables.core.VariableText;
import de.walware.ecommons.variables.core.VariableText.LocationProcessor;

import de.walware.statet.nico.core.runtime.ToolController;
import de.walware.statet.nico.core.runtime.ToolWorkspace;

import de.walware.statet.r.core.RUtil;
import de.walware.statet.r.core.model.IRLangSourceElement;
import de.walware.statet.r.core.model.IRSourceUnit;
import de.walware.statet.r.core.model.IRWorkspaceSourceUnit;
import de.walware.statet.r.core.rsource.ast.GenericVisitor;
import de.walware.statet.r.core.rsource.ast.RAstNode;
import de.walware.statet.r.internal.debug.ui.RControllerCodeLaunchConnector;
import de.walware.statet.r.internal.debug.ui.RLaunchingMessages;
import de.walware.statet.r.internal.debug.ui.launcher.LaunchShortcutUtil;
import de.walware.statet.r.internal.debug.ui.launcher.RCodeLaunchRegistry;
import de.walware.statet.r.internal.debug.ui.launcher.RCodeLaunchRegistry.ContentHandler.FileCommand;
import de.walware.statet.r.nico.AbstractRController;
import de.walware.statet.r.nico.IRModelSrcref;
import de.walware.statet.r.ui.RUI;


/**
 * Provides methods to submit code to R
 * 
 * The methods use the code launch connector selected in the preferences
 * and therefore it supports external consoles too
 * (in contrast to direct usage of new {@link AbstractRController}).
 */
public final class RCodeLaunching {
	
	
	public static final String RUN_SELECTION_COMMAND_ID = "de.walware.statet.r.commands.RunSelectionInR"; //$NON-NLS-1$
	
	public static final String RUN_SELECTION_GOTOCONSOLE_COMMAND_ID = "de.walware.statet.r.commands.RunSelectionInR_GotoConsole"; //$NON-NLS-1$
	
	public static final String RUN_SELECTION_PASTEOUTPUT_COMMAND_ID = "de.walware.statet.r.commands.RunSelectionInR_PasteOutput"; //$NON-NLS-1$
	
	public static final String RUN_FILEVIACOMMAND_COMMAND_ID = "de.walware.statet.r.commands.RunFileViaCommand"; //$NON-NLS-1$
	
	public static final String RUN_FILEVIACOMMAND_GOTOCONSOLE_COMMAND_ID = "de.walware.statet.r.commands.RunFileViaCommand_GotoConsole"; //$NON-NLS-1$
	
	
	public static final String FILE_COMMAND_ID_PARAMTER_ID = "fileCommandId"; //$NON-NLS-1$
	
	
	private static final IStringVariable FILE_NAME_VARIABLE = new StringVariable("resource_loc", "The complete path of the source file"); //$NON-NLS-1$
	private static final IStringVariable FILE_ENCODING_VARIABLE = new StringVariable("resource_encoding", "The encoding of the source file"); //$NON-NLS-1$
	private static final IStringVariable ECHO_ENABLED_VARIABLE = new StringVariable("echo", "If echo is enabled"); //$NON-NLS-1$
	
	private static final IStatus STATUS_PROMPTER = new Status(IStatus.INFO, IDebugUIConstants.PLUGIN_ID, 200, "", null); //$NON-NLS-1$
	private static final IStatus STATUS_SAVE = new Status(IStatus.INFO, DebugPlugin.getUniqueIdentifier(), 222, "", null); //$NON-NLS-1$
	
	
	public static class SourceRegion implements IRModelSrcref, IAdaptable {
		
		private static final String MARKER_TYPE = "org.eclipse.core.resources.marker";
		
		private static class ElementSearcher extends GenericVisitor {
			
			
			private List<IRLangSourceElement> fList;
			
			
			@Override
			public void visitNode(final RAstNode node) throws InvocationTargetException {
				final Object[] attachments = node.getAttachments();
				for (int i = 0; i < attachments.length; i++) {
					if (attachments[i] instanceof IRLangSourceElement) {
						if (fList == null) {
							fList = new ArrayList<IRLangSourceElement>();
						}
						fList.add((IRLangSourceElement) attachments[i]);
					}
				}
				if (fList == null) {
					super.visitNode(node);
				}
			}
			
		}
		
		
		private final IRSourceUnit fSourceUnit;
		private RAstNode fNode;
		private List<IRLangSourceElement> fElements;
		
		private final AbstractDocument fDocument;
		
		private int fBeginOffset = -1;
		private int fBeginLine = -1;
		private int fBeginColumn = -1;
		private int fEndOffset = -1;
		private int fEndLine = -1;
		private int fEndColumn = -1;
		
		private String fCode;
		
		private IMarker fMarker;
		
		
		public SourceRegion(final IRSourceUnit file, final AbstractDocument document) {
			fSourceUnit = file;
			fDocument = document;
		}
		
		
		public IRSourceUnit getFile() {
			return fSourceUnit;
		}
		
		public void setNode(final RAstNode node) {
			fNode = node;
		}
		
		public List<IRLangSourceElement> getElements() {
			if (fElements == null) {
				if (fNode != null) {
					final ElementSearcher searcher = new ElementSearcher();
					try {
						searcher.visitNode(fNode);
						fElements = searcher.fList;
					}
					catch (final InvocationTargetException e) {}
				}
				if (fElements == null) {
					fElements = Collections.emptyList();
				}
			}
			return fElements;
		}
		
		
		public void setCode(final String code) {
			fCode = code;
		}
		
		public void setBegin(final int offset) throws BadLocationException {
			fBeginOffset = offset;
			fBeginLine = fDocument.getLineOfOffset(fBeginOffset);
			fBeginColumn = TextUtil.getColumn(fDocument, fBeginOffset, fBeginLine, 8);
		}
		
		public boolean hasBeginDetail() {
			return (fBeginLine >= 0 && fBeginColumn >= 0);
		}
		
		public int getOffset() {
			return fBeginOffset;
		}
		
		public int getFirstLine() {
			return fBeginLine;
		}
		
		public int getFirstColumn() {
			return fBeginColumn;
		}
		
		public void setEnd(final int offset) throws BadLocationException {
			fEndOffset = offset;
			fEndLine = fDocument.getLineOfOffset(fEndOffset);
			fEndColumn = TextUtil.getColumn(fDocument, fEndOffset-1, fEndLine, 8);
		}
		
		public boolean hasEndDetail() {
			return (fEndLine >= 0 && fEndColumn >= 0);
		}
		
		public int getLength() {
			return fEndOffset-fBeginOffset;
		}
		
		public int getLastLine() {
			return fEndLine;
		}
		
		public int getLastColumn() {
			return fEndColumn;
		}
		
		
		void installMarker() {
			if (fSourceUnit instanceof IRWorkspaceSourceUnit) {
				final IResource resource = ((IRWorkspaceSourceUnit) fSourceUnit).getResource();
				try {
					fMarker = resource.createMarker(MARKER_TYPE);
					fMarker.setAttribute(IMarker.CHAR_START, fBeginOffset);
					fMarker.setAttribute(IMarker.CHAR_END, fEndOffset);
				}
				catch (final CoreException e) {
					StatusManager.getManager().handle(new Status(IStatus.ERROR, RUI.PLUGIN_ID, 0,
							"An error occurred when creating code position marker.", e));
				}
			}
		}
		
		void disposeMarker() {
			try {
				fMarker.delete();
				fMarker = null;
			}
			catch (final CoreException e) {
				StatusManager.getManager().handle(new Status(IStatus.ERROR, RUI.PLUGIN_ID, 0,
						"An error occurred when removing code position marker.", e));
			}
		}
		
		public Object getAdapter(final Class required) {
			if (IMarker.class.equals(required)) {
				return fMarker;
			}
			return null;
		}
		
	}
	
	
	public static void gotoRConsole() throws CoreException {
		final IRCodeLaunchConnector connector = RCodeLaunchRegistry.getDefault().getConnector();
		
		connector.gotoConsole();
	}
	
	public static String getFileCommand(final String id) {
		final FileCommand fileCommand = RCodeLaunchRegistry.getDefault().getFileCommand(id);
		if (fileCommand != null) {
			return fileCommand.getCurrentCommand();
		}
		return null;
	}
	
	public static String getPreferredFileCommand(final String contentType) {
		final FileCommand fileCommand = RCodeLaunchRegistry.getDefault().getContentFileCommand(contentType);
		return fileCommand.getCurrentCommand();
	}
	
	public static ICodeLaunchContentHandler getCodeLaunchContentHandler(final String contentType) {
		return RCodeLaunchRegistry.getDefault().getContentHandler(contentType);
	}
	
	
	/**
	 * Runs a file related command in R.
	 * Use this method only, if you don't have an IFile or IPath object for your file
	 * (e.g. file on webserver).
	 * <p>
	 * The pattern ${file} in command string is replaced by the path of
	 * the specified file.</p>
	 * 
	 * @param command the command, (at moment) should be single line.
	 * @param file the file.
	 * @throws CoreException if running failed.
	 */
	public static void runFileUsingCommand(final String command, final URI fileURI,
			final ISourceUnit su, final String encoding, final boolean gotoConsole) throws CoreException {
		if (su instanceof IWorkspaceSourceUnit && su.getResource() instanceof IFile) {
			final IFile file = (IFile) ((IWorkspaceSourceUnit) su).getResource();
			
			// save files
			final IProject project = file.getProject();
			if (project != null) {
				final IProject[] referencedProjects = project.getReferencedProjects();
				final IProject[] allProjects = new IProject[referencedProjects.length+1];
				allProjects[0] = project;
				System.arraycopy(referencedProjects, 0, allProjects, 1, referencedProjects.length);
				if (!saveBeforeLaunch(allProjects)) {
					return;
				}
			}
		}
		
		final IRCodeLaunchConnector connector = RCodeLaunchRegistry.getDefault().getConnector();
		IFileStore fileStore = null;
		try {
			fileStore = EFS.getStore(fileURI);
		}
		catch (final CoreException e) {
			fileStore = null;
		}
		
		if (fileStore != null && connector instanceof RControllerCodeLaunchConnector) {
			final IFileStore store = fileStore;
			((RControllerCodeLaunchConnector) connector).submit(new RControllerCodeLaunchConnector.CommandsCreator() {
				public IStatus submitTo(final ToolController controller) {
					final ToolWorkspace workspace = controller.getWorkspaceData();
					try {
						final String path = workspace.toToolPath(store);
						final String code = resolveVariables(command, path, encoding);
						final IToolRunnable runnable = new RunFileViaCommandRunnable(
								PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJ_FILE),
								"Run '" + store.toString() + "'", // TODO custom image and label
								code, su );
						final IStatus status = controller.getTool().getQueue().add(runnable);
						if (!status.isOK()) {
							runnable.changed(IToolRunnable.BEING_ABANDONED, null);
						}
						return status;
					}
					catch (final CoreException e) {
						return e.getStatus();
					}
				}
			}, gotoConsole);
		}
		else {
			String path = null;
			try {
				if (EFS.getLocalFileSystem().equals(EFS.getFileSystem(fileURI.getScheme()))) {
					path = EFS.getLocalFileSystem().getStore(fileURI).toString();
				}
			} catch (final CoreException e) {
			}
			if (path == null) {
				path = fileURI.toString();
			}
			final String code = resolveVariables(command, path, encoding);
			connector.submit(new String[] { code }, gotoConsole);
		}
	}
	
	private static String resolveVariables(final String command, final String path, final String encoding)
			throws CoreException {
		final List<IDynamicVariable> variables = new ArrayList<IDynamicVariable>();
		variables.add(new DynamicVariable(FILE_NAME_VARIABLE) {
			public String getValue(final String argument) throws CoreException {
				return RUtil.escapeCompletely(path);
			}
		});
		variables.add(new DynamicVariable(FILE_ENCODING_VARIABLE) {
			public String getValue(final String argument) throws CoreException {
				return encoding != null ? RUtil.escapeCompletely(encoding) : "unknown";
			}
		});
		variables.add(new DynamicVariable(ECHO_ENABLED_VARIABLE) {
			public String getValue(final String argument) throws CoreException {
				final Boolean echo = PreferencesUtil.getInstancePrefs().getPreferenceValue(
						LaunchShortcutUtil.ECHO_ENABLED_PREF);
				return (echo != null && echo.booleanValue()) ?
						"TRUE" : "FALSE";
			}
		});
		final VariableText text = new VariableText(command, variables, true);
		text.performFinalStringSubstitution(new LocationProcessor() {
			public String process(final String path) throws CoreException {
				return RUtil.escapeCompletely(path);
			}
		});
		return text.getText();
	}
	
	private static boolean saveBeforeLaunch(final IProject[] projects) throws CoreException {
		IStatusHandler prompter = null;
		prompter = DebugPlugin.getDefault().getStatusHandler(STATUS_PROMPTER);
		if (prompter != null) {
			return ((Boolean) prompter.handleStatus(STATUS_SAVE,
					new Object[] { null, projects } )).booleanValue();
		}
		return true;
	}
	
	public static boolean runRCodeDirect(final String[] lines, final boolean gotoConsole,
			final IProgressMonitor monitor) throws CoreException {
		if (monitor != null) {
			monitor.subTask(RLaunchingMessages.RCodeLaunch_SubmitCode_task);
		}
		final IRCodeLaunchConnector connector = RCodeLaunchRegistry.getDefault().getConnector();
		
		return connector.submit(lines, gotoConsole);
	}
	
	public static boolean runRCodeDirect(final String code, final boolean gotoConsole) throws CoreException {
		final List<String> lines = new ArrayList<String>(2 + code.length()/30);
		listLines(code, lines);
		return runRCodeDirect(lines.toArray(new String[lines.size()]), gotoConsole, null);
	}
	
	public static boolean runRCodeDirect(final List<SourceRegion> codeRegions,
			final boolean gotoConsole) throws CoreException {
		final IRCodeLaunchConnector connector = RCodeLaunchRegistry.getDefault().getConnector();
		if (connector instanceof RControllerCodeLaunchConnector) {
			return ((RControllerCodeLaunchConnector) connector).submit(
					new RControllerCodeLaunchConnector.CommandsCreator() {
				public IStatus submitTo(final ToolController controller) {
					final IToolRunnable[] runnables = new IToolRunnable[codeRegions.size()];
					final List<String> lines = new ArrayList<String>();
					for (int i = 0; i < runnables.length; i++) {
						final SourceRegion region = codeRegions.get(i);
						lines.clear();
						listLines(region.fCode, lines);
						runnables[i] = new RunEntireCommandRunnable(
								lines.toArray(new String[lines.size()]), region);
					}
					final IStatus status = controller.getTool().getQueue().add(runnables);
					if (!status.isOK()) {
						for (int i = 0; i < runnables.length; i++) {
							runnables[i].changed(IToolRunnable.BEING_ABANDONED, null);
						}
					}
					return status;
				}
			}, gotoConsole);
		}
		final List<String> lines = new ArrayList<String>(codeRegions.size()*2);
		for (int i = 0; i < codeRegions.size(); i++) {
			listLines(codeRegions.get(i).fCode, lines);
		}
		return runRCodeDirect(lines.toArray(new String[lines.size()]), gotoConsole, null);
	}
	
	private static void listLines(final String text, final List<String> lines) {
		final int n = text.length();
		int i = 0;
		int lineStart = 0;
		while (i < n) {
			switch (text.charAt(i)) {
			case '\r':
				lines.add(text.substring(lineStart, i));
				i++;
				if (i < n && text.charAt(i) == '\n') {
					i++;
				}
				lineStart = i;
				continue;
			case '\n':
				lines.add(text.substring(lineStart, i));
				i++;
				if (i < n && text.charAt(i) == '\r') {
					i++;
				}
				lineStart = i;
				continue;
			default:
				i++;
				continue;
			}
		}
		if (lineStart < n) {
			lines.add(text.substring(lineStart, n));
		}
	}
	
	private static String firstLine(final String text) {
		final int n = text.length();
		int i = 0;
		int start = -1;
		int end = -1;
		final StringBuilder sb = new StringBuilder(80);
		while (i < n) {
			final int c = text.charAt(i);
			switch (c) {
			case '\r':
			case '\n':
			case '\f':
				if (start >= 0) {
					if (sb.length() > 0) {
						sb.append(' ');
					}
					sb.append(text, start, end);
					if (sb.length() >= 30) {
						return sb.toString();
					}
					start = -1;
				}
				i++;
				continue;
			case ' ':
			case '\t':
				if (start >= 0 && sb.length() + (end - start) >= 60) {
					if (sb.length() > 0) {
						sb.append(' ');
					}
					sb.append(text, start, end);
					return sb.toString();
				}
				i++;
				continue;
			default:
				if (start < 0) {
					start = i;
				}
				end = ++i;
				continue;
			}
		}
		if (start >= 0) {
			if (sb.length() > 0) {
				sb.append(' ');
			}
			sb.append(text, start, end);
		}
		return sb.toString();
	}
	
}
