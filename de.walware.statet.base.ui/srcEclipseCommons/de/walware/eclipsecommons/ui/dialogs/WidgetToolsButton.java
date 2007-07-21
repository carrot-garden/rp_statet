/*******************************************************************************
 * Copyright (c) 2007 WalWare/StatET-Project (www.walware.de/goto/statet).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Stephan Wahlbrink - initial API and implementation
 *******************************************************************************/

package de.walware.eclipsecommons.ui.dialogs;

import org.eclipse.jface.resource.FontDescriptor;
import org.eclipse.jface.resource.FontRegistry;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;


/**
 * Provides a tools menu button.
 */
public class WidgetToolsButton extends Composite {
	
	
	static final String FONT_SYMBOLIC_NAME = "de.walware.toolbuttonfont"; //$NON-NLS-1$
	
	static Font getToolButtonFont() {
		FontRegistry fontRegistry = JFaceResources.getFontRegistry();
		if (!fontRegistry.hasValueFor(FONT_SYMBOLIC_NAME)) {
			fontRegistry.addListener(new IPropertyChangeListener() {
				public void propertyChange(PropertyChangeEvent event) {
					if (event.getProperty().equals(JFaceResources.DIALOG_FONT)) {
						updateFont();
					}
				}
			});
			updateFont();
		}
		return fontRegistry.get(FONT_SYMBOLIC_NAME);
	}
	private static void updateFont() {
		FontRegistry fontRegistry = JFaceResources.getFontRegistry();
		Font dialogFont = fontRegistry.get(JFaceResources.DIALOG_FONT);
		int size = Math.max(dialogFont.getFontData()[0].getHeight()*3/5, 7);
		FontDescriptor descriptor = fontRegistry.getDescriptor(JFaceResources.TEXT_FONT).setHeight(size);
		Font toolFont = descriptor.createFont(Display.getCurrent());
		fontRegistry.put(FONT_SYMBOLIC_NAME, toolFont.getFontData());
	}
	
	
	private Button fButton;
	private Menu fMenu;
	
	
	public WidgetToolsButton(Control target) {
		super(target.getParent(), SWT.NONE);
		setLayout(new FillLayout());
		createButton(target);
	}
	
	@Override
	public void setFont(Font font) {
		super.setFont(font);
		fButton.setFont(getToolButtonFont());
	}
	
	@Override
	public Point computeSize(int hint, int hint2, boolean changed) {
		Point computeSize = super.computeSize(hint, hint2, changed);
		int y = Math.max(computeSize.y-2, 18);
		int x = Math.max(computeSize.x-2, y);
		return new Point(x, y);
	}

	protected void createButton(Control target) {
		Composite parent = target.getParent();
		
		fButton = new Button(this, (SWT.PUSH | SWT.CENTER));
		updateLabels(false);
		fButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				showMenu();
			}
		});
		fButton.addKeyListener(new KeyListener() {
			public void keyPressed(KeyEvent e) {
			}
			public void keyReleased(KeyEvent e) {
				if (e.character == '+') {
					showMenu();
				}
			}
		});
		
		target.addFocusListener(new FocusListener() {
			public void focusGained(FocusEvent e) {
				updateLabels(true);
			}
			public void focusLost(FocusEvent e) {
				updateLabels(false);
			}
		});
		
		fButton.addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent e) {
				disposeMenu();
			}
		});
		setFont(null);
	}
	
	public void showMenu() {
		if (fMenu == null) {
			fMenu = new Menu(fButton);
			fillMenu(fMenu);
		}
		Rectangle bounds = fButton.getBounds();
		Point pos = fButton.getParent().toDisplay(bounds.x, bounds.y + bounds.height);
		fMenu.setLocation(pos);
		fMenu.setVisible(true);
	}
	
	public void resetMenu() {
		disposeMenu();
	}
	
	protected void disposeMenu() {
		if (fMenu != null) {
			fMenu.dispose();
			fMenu = null;
		}
	}

	protected void updateLabels(boolean hasFocus) {
		fButton.setText(hasFocus ? "&+" : "+"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	protected void fillMenu(Menu menu) {
	}
}