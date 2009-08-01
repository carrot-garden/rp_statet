/*******************************************************************************
 * Copyright (c) 2000, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package de.walware.statet.r.internal.ui.refactoring;

import java.util.List;
import java.util.Map;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.CellLabelProvider;
import org.eclipse.jface.viewers.ColumnPixelData;
import org.eclipse.jface.viewers.ColumnViewer;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.ltk.ui.refactoring.UserInputWizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.TraverseEvent;
import org.eclipse.swt.events.TraverseListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;

import de.walware.ecommons.ui.dialogs.CellEditorWizardStatusUpdater;
import de.walware.ecommons.ui.util.LayoutUtil;
import de.walware.ecommons.ui.util.ViewerUtil;
import de.walware.ecommons.ui.util.ViewerUtil.TreeComposite;

import de.walware.statet.r.core.model.IRFrame;
import de.walware.statet.r.core.model.IRFrameInSource;
import de.walware.statet.r.core.model.IRLangElement;
import de.walware.statet.r.core.refactoring.RenameInRegionRefactoring;
import de.walware.statet.r.core.refactoring.RenameInRegionRefactoring.Variable;
import de.walware.statet.r.internal.ui.RIdentifierCellValidator;
import de.walware.statet.r.ui.RLabelProvider;


public class RenameInRegionWizard extends RefactoringWizard {
	
	
	private static class RenameInRegionInputPage extends UserInputWizardPage {
		
		public static final String PAGE_NAME = "RenameInRegion.InputPage"; //$NON-NLS-1$
		
		private static class NameEditing extends EditingSupport {
			
			private TextCellEditor fCellEditor;
			
			public NameEditing(final ColumnViewer viewer) {
				super(viewer);
				fCellEditor = new TextCellEditor((Composite) viewer.getControl());
				fCellEditor.getControl().setFont(JFaceResources.getTextFont());
				fCellEditor.setValidator(new RIdentifierCellValidator());
			}
			
			@Override
			protected boolean canEdit(final Object element) {
				return (element instanceof Variable);
			}
			
			@Override
			protected CellEditor getCellEditor(final Object element) {
				if (element instanceof Variable) {
					return fCellEditor;
				}
				return null;
			}
			
			@Override
			protected Object getValue(final Object element) {
				if (element instanceof Variable) {
					final Variable variable = (Variable) element;
					final String name = variable.getNewName();
					if (name != null) {
						return name;
					}
					return variable.getName();
				}
				return null;
			}
			
			@Override
			protected void setValue(final Object element, final Object value) {
				if (element instanceof Variable) {
					final Variable variable = (Variable) element;
					variable.setNewName((String) value);
					getViewer().update(element, null);
				}
			}
			
		}
		
		
		private static final ViewerFilter[] FILTER_RESOLVED = new ViewerFilter[] {
			new ViewerFilter() {
				@Override
				public boolean select(final Viewer viewer, final Object parentElement, final Object element) {
					if (element instanceof Variable) {
						final Variable variable = (Variable) element;
						final IRFrameInSource frame = variable.getFrame();
						return frame.isResolved(variable.getName());
					}
					return true;
				}
			}
		};
		
		private static final ViewerFilter[] FILTER_OFF = new ViewerFilter[0];
		
		
		private TreeViewer fVariablesViewer;
		
		
		public RenameInRegionInputPage() {
			super(PAGE_NAME);
		}
		
		@Override
		protected RenameInRegionRefactoring getRefactoring() {
			return (RenameInRegionRefactoring) super.getRefactoring();
		}
		
		public void createControl(final Composite parent) {
			final Composite composite = new Composite(parent, SWT.NONE);
			composite.setLayout(LayoutUtil.applyDialogDefaults(new GridLayout(), 2));
			setControl(composite);
			composite.setFont(JFaceResources.getDialogFont());
			initializeDialogUnits(composite);
			
			final Map<IRFrame, Map<String, Variable>> variables = getRefactoring().getVariables();
			
			{	final Label label = new Label(composite, SWT.NONE);
				label.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 2, 1));
				label.setText(Messages.RenameInRegion_Wizard_header);
				label.setFont(JFaceResources.getBannerFont());
			}
			
			LayoutUtil.addSmallFiller(composite, false);
			
			final Control table = createVariablesTable(composite);
			table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));
			
			LayoutUtil.addSmallFiller(composite, false);
			Dialog.applyDialogFont(composite);
//			PlatformUI.getWorkbench().getHelpSystem().setHelp(getControl(), );
			
			fVariablesViewer.setFilters(FILTER_RESOLVED);
			fVariablesViewer.setInput(getRefactoring().getVariables());
		}
		
		private Control createVariablesTable(final Composite parent) {
			final Composite composite = new Composite(parent, SWT.NONE);
			composite.setLayout(LayoutUtil.applyCompositeDefaults(new GridLayout(), 2));
			
			{	final Composite above = new Composite(composite, SWT.NONE);
				above.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false, 2, 1));
				above.setLayout(LayoutUtil.applyCompositeDefaults(new GridLayout(), 2));
				
				final Label label = new Label(above, SWT.NONE);
				label.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));
				label.setText("Enter new variable name(s):");
				label.addTraverseListener(new TraverseListener() {
					public void keyTraversed(final TraverseEvent e) {
						if (e.detail == SWT.TRAVERSE_MNEMONIC) {
							e.doit = false;
							fVariablesViewer.getControl().setFocus();
						}
					}
				});
				
				final Button showAll = new Button(above, SWT.CHECK);
				showAll.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false, 1, 1));
				showAll.setText("Show &all identifiers");
				showAll.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(final SelectionEvent e) {
						if (showAll.getSelection()) {
							fVariablesViewer.setFilters(FILTER_OFF);
						}
						else {
							fVariablesViewer.setFilters(FILTER_RESOLVED);
						}
					}
				});
			}
			
			final TreeComposite table = new TreeComposite(composite, SWT.BORDER | SWT.SINGLE | SWT.FULL_SELECTION);
			table.tree.setHeaderVisible(true);
			table.tree.setLinesVisible(true);
			final GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1);
			gd.heightHint = LayoutUtil.hintHeight(table.tree, 12);
			table.setLayoutData(gd);
			table.viewer.setAutoExpandLevel(TreeViewer.ALL_LEVELS);
			fVariablesViewer = table.viewer;
			
			{	final TreeViewerColumn column = new TreeViewerColumn(table.viewer, SWT.NONE);
				column.getColumn().setText("Scope / Variable");
				table.layout.setColumnData(column.getColumn(), new ColumnWeightData(1));
				column.setLabelProvider(new CellLabelProvider() {
					private RLabelProvider fFrameLabelProvider = new RLabelProvider();
					@Override
					public void update(final ViewerCell cell) {
						cell.setBackground(null);
						final Object element = cell.getElement();
						if (element instanceof IRFrame) {
							cell.setFont(JFaceResources.getDialogFont());
							final IRFrame frame = (IRFrame) element;
							final List<? extends IRLangElement> modelElements = frame.getModelElements();
							if (modelElements.size() > 0) {
								fFrameLabelProvider.update(cell, modelElements.get(0));
								return;
							}
//							final IElementName elementName = frame.getElementName();
//							if (elementName != null) {
//								cell.setText(elementName.getDisplayName());
//								return;
//							}
							cell.setImage(null);
							cell.setText(frame.getFrameId());
							cell.setStyleRanges(null);
							return;
						}
						if (element instanceof Variable) {
							cell.setFont(JFaceResources.getDialogFont());
							final Variable variable = (Variable) element;
							final String name = variable.getName();
							cell.setImage(null);
							cell.setText(name);
							cell.setStyleRanges(null);
							return;
						}
						cell.setImage(null);
						cell.setText(""); //$NON-NLS-1$
						cell.setStyleRanges(null);
					}
				});
			}
			{	final TreeViewerColumn column = new TreeViewerColumn(table.viewer, SWT.NONE);
				column.getColumn().setText("New Name");
				table.layout.setColumnData(column.getColumn(), new ColumnWeightData(1));
				column.setLabelProvider(new CellLabelProvider() {
					@Override
					public void update(final ViewerCell cell) {
						final Object element = cell.getElement();
						if (element instanceof Variable) {
							cell.setFont(JFaceResources.getTextFont());
							final Variable variable = (Variable) element;
							final String name = variable.getNewName();
							if (name != null) {
								cell.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_YELLOW));
								cell.setText(name);
								cell.setStyleRanges(null);
							}
							else {
								cell.setBackground(null);
								cell.setText(variable.getName());
								cell.setStyleRanges(null);
							}
							return;
						}
						cell.setBackground(null);
						cell.setText(""); //$NON-NLS-1$
						cell.setStyleRanges(null);
					}
				});
				final NameEditing editing = new NameEditing(table.viewer);
				new CellEditorWizardStatusUpdater(editing.fCellEditor, RenameInRegionInputPage.this);
				column.setEditingSupport(editing);
			}
			{	final TreeViewerColumn column = new TreeViewerColumn(table.viewer, SWT.NONE);
				column.getColumn().setText(""); 
				table.layout.setColumnData(column.getColumn(), new ColumnPixelData(convertWidthInCharsToPixels(5)));
				column.getColumn().setToolTipText("Occurrences Count");
				column.setLabelProvider(new CellLabelProvider() {
					@Override
					public void update(final ViewerCell cell) {
						cell.setBackground(null);
						cell.setFont(JFaceResources.getDialogFont());
						final Object element = cell.getElement();
						if (element instanceof Variable) {
							final Variable variable = (Variable) element;
							cell.setText(Integer.toString(variable.getOccurrencesCount()));
							cell.setStyleRanges(null);
							return;
						}
						cell.setText(""); //$NON-NLS-1$
						cell.setStyleRanges(null);
					}
				});
			}
			
			ViewerUtil.installDefaultEditBehaviour(table.viewer);
			
			table.viewer.setContentProvider(new ITreeContentProvider() {
				private Map<IRFrame, Map<String, Variable>> fVariables;
				public void inputChanged(final Viewer viewer, final Object oldInput, final Object newInput) {
					fVariables = (Map<IRFrame, Map<String, Variable>>) newInput;
				}
				public Object[] getElements(final Object inputElement) {
					return fVariables.keySet().toArray();
				}
				public Object getParent(final Object element) {
					if (element instanceof Variable) {
						return ((Variable) element).getFrame();
					}
					return null;
				}
				public boolean hasChildren(final Object element) {
					if (element instanceof IRFrame) {
						return true;
					}
					return false;
				}
				public Object[] getChildren(final Object parentElement) {
					if (parentElement instanceof IRFrame) {
						return fVariables.get(parentElement).values().toArray();
					}
					return null;
				}
				public void dispose() {
				}
			});
			
			ViewerUtil.addDoubleClickExpansion(table.viewer);
			
			return composite;
		}
		
		@Override
		public void setVisible(final boolean visible) {
			super.setVisible(visible);
			fVariablesViewer.getControl().setFocus();
		}
		
	}
	
	
	public RenameInRegionWizard(final RenameInRegionRefactoring ref) {
		super(ref, DIALOG_BASED_USER_INTERFACE | PREVIEW_EXPAND_FIRST_NODE | NO_BACK_BUTTON_ON_STATUS_DIALOG);
		setDefaultPageTitle(Messages.RenameInRegion_Wizard_title);
	}
	
	
	@Override
	protected void addUserInputPages() {
		addPage(new RenameInRegionInputPage());
	}
	
}