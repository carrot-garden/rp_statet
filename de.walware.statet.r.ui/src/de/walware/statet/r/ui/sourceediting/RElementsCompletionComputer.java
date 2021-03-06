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

package de.walware.statet.r.ui.sourceediting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.ibm.icu.text.Collator;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.text.AbstractDocument;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.BadPartitioningException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITypedRegion;

import de.walware.ecommons.ltk.AstInfo;
import de.walware.ecommons.ltk.IElementName;
import de.walware.ecommons.ltk.IModelElement;
import de.walware.ecommons.ltk.ISourceUnit;
import de.walware.ecommons.ltk.ISourceUnitModelInfo;
import de.walware.ecommons.ltk.LTK;
import de.walware.ecommons.ltk.ast.AstSelection;
import de.walware.ecommons.ltk.ast.IAstNode;
import de.walware.ecommons.ltk.ui.IElementLabelProvider;
import de.walware.ecommons.ltk.ui.sourceediting.AssistInvocationContext;
import de.walware.ecommons.ltk.ui.sourceediting.AssistProposalCollector;
import de.walware.ecommons.ltk.ui.sourceediting.IAssistCompletionProposal;
import de.walware.ecommons.ltk.ui.sourceediting.IAssistInformationProposal;
import de.walware.ecommons.ltk.ui.sourceediting.IContentAssistComputer;
import de.walware.ecommons.ltk.ui.sourceediting.ISourceEditor;
import de.walware.ecommons.text.IPartitionConstraint;
import de.walware.ecommons.ts.ITool;

import de.walware.statet.nico.core.runtime.ToolProcess;
import de.walware.statet.nico.ui.NicoUITools;
import de.walware.statet.nico.ui.console.ConsolePageEditor;
import de.walware.statet.nico.ui.console.InputDocument;

import de.walware.rj.data.RReference;

import de.walware.statet.r.console.core.RProcess;
import de.walware.statet.r.console.core.RWorkspace;
import de.walware.statet.r.console.core.RWorkspace.ICombinedREnvironment;
import de.walware.statet.r.core.RSymbolComparator;
import de.walware.statet.r.core.data.ICombinedRElement;
import de.walware.statet.r.core.model.ArgsDefinition;
import de.walware.statet.r.core.model.IPackageReferences;
import de.walware.statet.r.core.model.IRElement;
import de.walware.statet.r.core.model.IRFrame;
import de.walware.statet.r.core.model.IRFrameInSource;
import de.walware.statet.r.core.model.IRMethod;
import de.walware.statet.r.core.model.IRModelInfo;
import de.walware.statet.r.core.model.IRSourceUnit;
import de.walware.statet.r.core.model.RElementAccess;
import de.walware.statet.r.core.model.RElementName;
import de.walware.statet.r.core.model.RModel;
import de.walware.statet.r.core.rlang.RTokens;
import de.walware.statet.r.core.rsource.IRDocumentPartitions;
import de.walware.statet.r.core.rsource.RHeuristicTokenScanner;
import de.walware.statet.r.core.rsource.ast.FCall;
import de.walware.statet.r.core.rsource.ast.FCall.Args;
import de.walware.statet.r.core.rsource.ast.NodeType;
import de.walware.statet.r.core.rsource.ast.RAstNode;
import de.walware.statet.r.internal.ui.editors.RArgumentListContextInformation;
import de.walware.statet.r.internal.ui.editors.RElementCompletionProposal;
import de.walware.statet.r.internal.ui.editors.RKeywordCompletionProposal;
import de.walware.statet.r.internal.ui.editors.RSimpleCompletionComputer;
import de.walware.statet.r.ui.RLabelProvider;


public class RElementsCompletionComputer implements IContentAssistComputer {
	
	
	private static final class ExactFCallPattern {
		
		private final IElementName fCodeName;
		private final String fAssignName;
		private final int fAssignLength;
		
		public ExactFCallPattern(final IElementName name) {
			fCodeName = name;
			if (fCodeName.getNextSegment() == null) {
				fAssignName = fCodeName.getSegmentName();
				fAssignLength = fAssignName.length();
			}
			else {
				fAssignName = null;
				fAssignLength = 0;
			}
		}
		
		public boolean matches(final IElementName candidateName) {
			String candidate0;
			return (fCodeName.equals(candidateName)
					|| (fAssignName != null && candidateName.getNextSegment() == null
							&& fAssignLength == (candidate0 = candidateName.getSegmentName()).length()-2
							&& fCodeName.getType() == candidateName.getType()
							&& candidate0.charAt(fAssignLength) == '<' && candidate0.charAt(fAssignLength+1) == '-'
							&& candidate0.regionMatches(false, 0, fAssignName, 0, fAssignLength) ));
		}
		
	}
	
	private static class FCallInfo {
		
		final FCall node;
		final RElementAccess access;
		
		public FCallInfo(final FCall node, final RElementAccess access) {
			this.node = node;
			this.access = access;
		}
		
	}
	
	
	private static final char[] F_BRACKETS = new char[] { '(', ')' };
	
	private static final IPartitionConstraint NO_R_COMMENT_CONSTRAINT = new IPartitionConstraint() {
		public boolean matches(final String partitionType) {
			return (partitionType != IRDocumentPartitions.R_COMMENT);
		};
	};
	
	private static final int LOCAL_ENVIR = 0;
	private static final int WS_ENVIR = 1;
	private static final int RUNTIME_ENVIR = 2;
	
	
	private static final List<String> fgKeywords;
	static {
		final ArrayList<String> list = new ArrayList<String>();
		Collections.addAll(list, RTokens.CONSTANT_WORDS);
		Collections.addAll(list, RTokens.FLOWCONTROL_WORDS);
		Collections.sort(list, Collator.getInstance());
		list.trimToSize();
		fgKeywords = Collections.unmodifiableList(list);
	}
	
	
	public static class CompleteRuntime extends RElementsCompletionComputer {
		
		public CompleteRuntime() {
			fCompleteRuntimeMode = true;
		}
		
	}
	
	
	private class EnvirIter implements Iterator<IRFrame> {
		
		private int fEnvirListIter0;
		private int fEnvirListIter1 = -1;
		private IRFrame fNext;
		
		public boolean hasNext() {
			if (fNext != null) {
				return true;
			}
			ITER_0 : while (fEnvirListIter0 < fEnvirList.length) {
				if (++fEnvirListIter1 < fEnvirList[fEnvirListIter0].size()) {
					fNext = fEnvirList[fEnvirListIter0].get(fEnvirListIter1);
					return true;
				}
				else {
					fEnvirListIter0++;
					fEnvirListIter1 = -1;
					continue ITER_0;
				}
			}
			return false;
		}
		
		public int getEnvirGroup() {
			return fEnvirListIter0;
		}
		
		public IRFrame next() {
			if (hasNext()) {
				final IRFrame frame = fNext;
				fNext = null;
				return frame;
			}
			return null;
		}
		
		public void remove() {
			throw new UnsupportedOperationException();
		}
		
	}
	
	
	private final IElementLabelProvider fLabelProvider = new RLabelProvider(RLabelProvider.NAMESPACE);
	private ISourceEditor fEditor;
	private RHeuristicTokenScanner fScanner;
	private ToolProcess<RWorkspace> fProcess;
	
	private final List<IRFrame>[] fEnvirList = new List[3];
	private Set<String> fEnvirListPackages;
	
	protected boolean fCompleteRuntimeMode;
	
	
	public RElementsCompletionComputer() {
		fEnvirList[RUNTIME_ENVIR] = new ArrayList<IRFrame>();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void sessionStarted(final ISourceEditor editor) {
		if (fEditor != editor) {
			fEditor = editor;
			fScanner = null;
			fProcess = null;
		}
		
		final ITool tool;
		if (fEditor instanceof ConsolePageEditor) {
			tool = (ITool) fEditor.getAdapter(ITool.class);
		}
		else {
			tool = NicoUITools.getTool(fEditor.getWorkbenchPart());
		}
		if (tool instanceof RProcess) {
			final RProcess rProcess = (RProcess) tool;
			final RWorkspace workspace = rProcess.getWorkspaceData();
			if (workspace.hasRObjectDB()) {
				fProcess = rProcess;
			}
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	public void sessionEnded() {
		fEnvirList[LOCAL_ENVIR] = null;
		fEnvirList[WS_ENVIR] = null;
		fEnvirList[RUNTIME_ENVIR].clear();
		fEnvirListPackages = null;
		fProcess = null;
	}
	
	private RHeuristicTokenScanner getScanner() {
		if (fScanner == null && fEditor != null) {
			fScanner = (RHeuristicTokenScanner) LTK.getModelAdapter(fEditor.getModelTypeId(),
					RHeuristicTokenScanner.class );
		}
		return fScanner;
	}
	
	
	private boolean isCompletable(IElementName elementName) {
		if (elementName == null) {
			return false;
		}
		do {
			switch (elementName.getType()) {
			case RElementName.SUB_INDEXED_S:
			case RElementName.SUB_INDEXED_D:
				return false;
			}
			if (elementName.getSegmentName() == null) {
				return false;
			}
			elementName = elementName.getNextSegment();
		}
		while (elementName != null);
		return true;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public IStatus computeCompletionProposals(final AssistInvocationContext context,
			final int mode, final AssistProposalCollector<IAssistCompletionProposal> proposals, final IProgressMonitor monitor) {
		if (mode == IContentAssistComputer.INFORMATION_MODE) {
			return computeContextInformation2(context, proposals, false, monitor);
		}
		
		if (context.getModelInfo() == null) {
			return null;
		}
		
		// Get node
		final AstSelection astSelection = context.getAstSelection();
		IAstNode node = astSelection.getCovering();
		if (node == null) {
			node = context.getAstInfo().root;
		}
		if (!(node instanceof RAstNode)) {
			return null;
		}
		
		// Get envir
		if (!initEnvirList(context, (RAstNode) node)) {
			return null;
		}
		
		// Get prefix
		final String prefix = context.getIdentifierPrefix();
		final RElementName prefixSegments = RElementName.parseDefault(prefix);
		if (prefixSegments == null) {
			return null;
		}
		
		// Collect proposals
		if (prefixSegments.getNextSegment() == null) {
			doComputeArgumentProposals(context, prefix, prefixSegments, proposals, monitor);
			doComputeMainProposals(context, prefix, prefixSegments, proposals, monitor);
			doComputeKeywordProposals(context, prefix, prefixSegments.getSegmentName(), proposals, monitor);
		}
		else {
			final String lastPrefix = computeSingleIdentifierPrefix(context);
			doComputeSubProposals(context, lastPrefix, prefixSegments, proposals, monitor);
		}
		return null;
	}
	
	protected void doComputeArgumentProposals(final AssistInvocationContext context,
			final String orgPrefix, final IElementName prefixName,
			final AssistProposalCollector<IAssistCompletionProposal> proposals, final IProgressMonitor monitor) {
		final String namePrefix = prefixName.getSegmentName();
		int offset = context.getInvocationOffset()-context.getIdentifierPrefix().length();
		IDocument document = context.getSourceViewer().getDocument();
		final RHeuristicTokenScanner scanner = getScanner();
		int indexShift = 0;
		if (document instanceof InputDocument) {
			final InputDocument inputDoc = (InputDocument) document;
			document = inputDoc.getMasterDocument();
			indexShift = inputDoc.getOffsetInMasterDocument();
			offset += indexShift;
		}
		if (scanner == null || offset < 2) {
			return;
		}
		scanner.configureDefaultParitions(document);
		if (scanner.getPartitioningConfig().getDefaultPartitionConstraint().matches(scanner.getPartition(offset-1).getType())) {
			final int index = scanner.findOpeningPeer(offset-1, F_BRACKETS);
			if (index >= 0) {
				final FCallInfo fcallInfo = searchFCallInfo(context, index-indexShift);
				if (fcallInfo != null) {
					final Args child = fcallInfo.node.getArgsChild();
					int sep = fcallInfo.node.getArgsOpenOffset()+indexShift;
					for (int argIdx = 0; argIdx < child.getChildCount()-1; argIdx++) {
						final int next = child.getSeparatorOffset(argIdx);
						if (next+indexShift < offset) {
							sep = next+indexShift;
						}
						else {
							break;
						}
					}
					fScanner.configure(document, NO_R_COMMENT_CONSTRAINT);
					if (sep+1 == offset
							|| fScanner.findNonBlankForward(sep+1, offset, true) < 0) {
						doComputeFCallArgumentProposals(context, offset-indexShift, fcallInfo, namePrefix, proposals);
					}
				}
			}
		}
	}
	
	protected void doComputeMainProposals(final AssistInvocationContext context,
			final String orgPrefix, final IElementName prefixName,
			final AssistProposalCollector<IAssistCompletionProposal> proposals, final IProgressMonitor monitor) {
		String namePrefix = prefixName.getSegmentName();
		if (namePrefix == null) {
			namePrefix = ""; //$NON-NLS-1$
		}
		final RSymbolComparator.PrefixPattern pattern = new RSymbolComparator.PrefixPattern(namePrefix); 
		final int offset = context.getInvocationOffset()-orgPrefix.length();
		final Set<String> mainNames = new HashSet<String>();
		final List<String> methodNames = new ArrayList<String>();
		
		int sourceLevel = 5;
		for (final EnvirIter iter = new EnvirIter(); iter.hasNext();) {
			final IRFrame envir = iter.next();
			int relevance;
			switch (envir.getFrameType()) {
			case IRFrame.CLASS:
			case IRFrame.FUNCTION:
			case IRFrame.EXPLICIT:
				relevance = Math.max(sourceLevel--, 1);
				break;
			case IRFrame.PROJECT:
				relevance = 1;
				break;
			case IRFrame.PACKAGE:
				relevance = -5;
				if (iter.getEnvirGroup() > 0 && namePrefix.length() == 0) {
					continue;
				}
				break;
			default:
				relevance = -10;
				break;
			}
			final List<? extends IRElement> elements = envir.getModelChildren(null);
			for (final IModelElement element : elements) {
				final IElementName elementName = element.getElementName();
				final int c1type = (element.getElementType() & IModelElement.MASK_C1);
				final boolean isRich = (c1type == IModelElement.C1_METHOD);
				if ((isRich || c1type == IModelElement.C1_VARIABLE)
						&& isCompletable(elementName)
						&& pattern.matches(elementName.getSegmentName())) {
					if ((relevance < 0) && !isRich
							&& mainNames.contains(elementName.getSegmentName()) ) {
						continue;
					}
					final IAssistCompletionProposal proposal = createProposal(context, offset, elementName, element, relevance);
					if (proposal != null) {
						if (elementName.getNextSegment() == null) {
							if (isRich) {
								methodNames.add(elementName.getSegmentName());
							}
							else {
								mainNames.add(elementName.getSegmentName());
							}
						}
						proposals.add(proposal);
					}
				}
			}
		}
		
		mainNames.addAll(methodNames);
		for (final EnvirIter iter = new EnvirIter(); iter.hasNext();) {
			final IRFrame envir = iter.next();
			if (envir instanceof IRFrameInSource) {
				final IRFrameInSource sframe = (IRFrameInSource) envir;
				final Set<String> elementNames = sframe.getAllAccessNames();
				for (final String candidate : elementNames) {
					if (candidate != null
							&& pattern.matches(candidate) 
							&& !mainNames.contains(candidate)
							&& !(candidate.equals(namePrefix) && (sframe.getAllAccessOfElement(candidate).size() <= 1)) ) {
						final IAssistCompletionProposal proposal = createProposal(context, orgPrefix, candidate);
						if (proposal != null) {
							mainNames.add(candidate);
							proposals.add(proposal);
						}
					}
				}
			}
		}
	}
	
	private void doComputeKeywordProposals(final AssistInvocationContext context,
			final String orgPrefix, final String prefix,
			final AssistProposalCollector<IAssistCompletionProposal> proposals, final IProgressMonitor monitor) {
		if (prefix.length() > 0 && orgPrefix.charAt(0) != '`') {
			final int offset = context.getInvocationOffset()-orgPrefix.length();
			final List<String> keywords = fgKeywords;
			for (final String keyword : keywords) {
				if (keyword.regionMatches(true, 0, prefix, 0, prefix.length())) {
					proposals.add(new RKeywordCompletionProposal(context, keyword, offset));
				}
			}
		}
	}
	
	protected String computeSingleIdentifierPrefix(final AssistInvocationContext context) {
		// like RAssistInvocationContext#computeIdentifierPrefix but only one single identifier
		final AbstractDocument document = (AbstractDocument) context.getSourceViewer().getDocument();
		int offset = context.getInvocationOffset();
		if (offset <= 0 || offset > document.getLength()) {
			return ""; 
		}
		try {
			ITypedRegion partition = document.getPartition(context.getEditor().getPartitioning().getPartitioning(), offset, true);
			if (partition.getType() == IRDocumentPartitions.R_QUOTED_SYMBOL) {
				offset = partition.getOffset();
			}
			int goodStart = offset;
			SEARCH_START: while (offset > 0) {
				final char c = document.getChar(offset - 1);
				if (RTokens.isRobustSeparator(c, false)) {
					switch (c) {
					case ':':
					case '$':
					case '@':
						break SEARCH_START;
					case ' ':
					case '\t':
						if (offset >= 2) {
							final char c2 = document.getChar(offset - 2);
							if ((offset == context.getInvocationOffset())
									&& !RTokens.isRobustSeparator(c, false)) {
								offset -= 2;
								continue SEARCH_START;
							}
						}
						break SEARCH_START;
					case '`':
						partition = document.getPartition(context.getEditor().getPartitioning().getPartitioning(), offset, false);
						if (partition.getType() == IRDocumentPartitions.R_QUOTED_SYMBOL) {
							offset = goodStart = partition.getOffset();
							break SEARCH_START;
						}
						else {
							break SEARCH_START;
						}
					
					default:
						break SEARCH_START;
					}
				}
				else {
					offset --;
					goodStart = offset;
					continue SEARCH_START;
				}
			}
			
			return document.get(offset, context.getInvocationOffset() - goodStart);
		}
		catch (final BadLocationException e) {
		}
		catch (final BadPartitioningException e) {
		}
		return ""; 
	}
	
	protected void doComputeSubProposals(final AssistInvocationContext context,
			final String orgPrefix, final RElementName prefixSegments,
			final AssistProposalCollector<IAssistCompletionProposal> proposals, final IProgressMonitor monitor) {
		int count = 0;
		IElementName prefixSegment = prefixSegments;
		while (true) {
			count++;
			if (prefixSegment.getNextSegment() != null) {
				prefixSegment = prefixSegment.getNextSegment();
				continue;
			}
			else {
				break;
			}
		}
		String namePrefix = prefixSegment.getSegmentName();
		if (namePrefix == null) {
			namePrefix = ""; //$NON-NLS-1$
		}
		final RSymbolComparator.PrefixPattern pattern = new RSymbolComparator.PrefixPattern(namePrefix);
		final int offset = context.getInvocationOffset()-orgPrefix.length();
		
		final Set<String> mainNames = new HashSet<String>();
		final List<String> methodNames = new ArrayList<String>();
		
		int sourceLevel = 5;
		for (final EnvirIter iter = new EnvirIter(); iter.hasNext();) {
			final IRFrame envir = iter.next();
			int relevance;
			switch (envir.getFrameType()) {
			case IRFrame.CLASS:
			case IRFrame.FUNCTION:
			case IRFrame.EXPLICIT:
				relevance = Math.max(sourceLevel--, 1);
				break;
			case IRFrame.PROJECT:
				relevance = 1;
				break;
			case IRFrame.PACKAGE:
				relevance = -5;
				break;
			default:
				relevance = -10;
				break;
			}
			final List<? extends IRElement> elements = envir.getModelChildren(null);
			ITER_ELEMENTS: for (final IModelElement rootElement : elements) {
				final IElementName elementName = rootElement.getElementName();
				final int c1type = (rootElement.getElementType() & IModelElement.MASK_C1);
				final boolean isRich = (c1type == IModelElement.C1_METHOD);
				if (isRich || c1type == IModelElement.C1_VARIABLE) {
					IModelElement element = rootElement;
					prefixSegment = prefixSegments;
					IElementName elementSegment = elementName;
					ITER_SEGMENTS: for (int i = 0; i < count-1; i++) {
						if (elementSegment == null) {
							final List<? extends IModelElement> children = element.getModelChildren(null);
							for (final IModelElement child : children) {
								elementSegment = child.getElementName();
								if (isCompletable(elementSegment)
										&& elementSegment.getSegmentName().equals(prefixSegment.getSegmentName())) {
									element = child;
									prefixSegment = prefixSegment.getNextSegment();
									elementSegment = elementSegment.getNextSegment();
									continue ITER_SEGMENTS;
								}
							}
							continue ITER_ELEMENTS;
						}
						else {
							if (isCompletable(elementSegment)
									&& elementSegment.getSegmentName().equals(prefixSegment.getSegmentName())) {
								prefixSegment = prefixSegment.getNextSegment();
								elementSegment = elementSegment.getNextSegment();
								continue ITER_SEGMENTS;
							}
							continue ITER_ELEMENTS;
						}
					}
					
					final boolean childMode;
					final List<? extends IModelElement> children;
					if (elementSegment == null) {
						childMode = true;
						if (element instanceof RReference) {
							element = (ICombinedRElement) ((RReference) element).getResolvedRObject();
							if (element == null) {
								continue;
							}
						}
						children = element.getModelChildren(null);
					}
					else {
						childMode = false;
						children = Collections.singletonList(element);
					}
					for (final IModelElement child : children) {
						if (childMode) {
							elementSegment = child.getElementName();
						}
						final String candidate = elementSegment.getSegmentName();
						if (isCompletable(elementSegment)
								&& pattern.matches(candidate) ) {
							if ((relevance > 0) && !isRich
									&& mainNames.contains(candidate) ) {
								continue ITER_ELEMENTS;
							}
							final IAssistCompletionProposal proposal = createProposal(context, offset, elementSegment, child, relevance);
							if (proposal != null) {
								if (elementSegment.getNextSegment() == null) {
									if (isRich) {
										methodNames.add(candidate);
									}
									else {
										mainNames.add(candidate);
									}
								}
								proposals.add(proposal);
							}
						}
					}
				}
			}
		}
		
		mainNames.addAll(methodNames);
		for (final EnvirIter iter = new EnvirIter(); iter.hasNext();) {
			final IRFrame envir = iter.next();
			if (envir instanceof IRFrameInSource) {
				final IRFrameInSource sframe = (IRFrameInSource) envir;
				final List<? extends RElementAccess> allAccess = sframe.getAllAccessOfElement(prefixSegments.getSegmentName());
				if (allAccess != null) {
					ITER_ELEMENTS: for (final RElementAccess elementAccess : allAccess) {
						IElementName elementSegment = elementAccess;
						ITER_SEGMENTS: for (int i = 0; i < count-1; i++) {
							if (isCompletable(elementSegment)
									&& elementSegment.getSegmentName().equals(prefixSegment.getSegmentName())) {
								prefixSegment = prefixSegment.getNextSegment();
								elementSegment = elementSegment.getNextSegment();
								continue ITER_SEGMENTS;
							}
							continue ITER_ELEMENTS;
						}
						
						if (elementSegment == null) {
							continue ITER_ELEMENTS;
						}
						final String candidate = elementSegment.getSegmentName();
						if (candidate != null && isCompletable(elementSegment)
								&& pattern.matches(candidate)
								&& !mainNames.contains(candidate)
								&& !candidate.equals(namePrefix) ) {
							final IAssistCompletionProposal proposal = createProposal(context, orgPrefix, candidate);
							if (proposal != null) {
								mainNames.add(candidate);
								proposals.add(proposal);
							}
						}
					}
				}
			}
		}
	}
	
	protected IAssistCompletionProposal createProposal(final AssistInvocationContext context, final String prefix, final String name) {
		final int offset = context.getInvocationOffset()-prefix.length();
		return new RSimpleCompletionComputer(context, name, offset);
	}
	
	protected IAssistCompletionProposal createProposal(final AssistInvocationContext context, final int offset, final IElementName elementName, final IModelElement element, final int relevance) {
		return new RElementCompletionProposal(context, elementName, offset, element, relevance, fLabelProvider);
	}
	
	/**
	 * {@inheritDoc}
	 */
	public IStatus computeContextInformation(final AssistInvocationContext context,
			final AssistProposalCollector<IAssistInformationProposal> proposals, final IProgressMonitor monitor) {
		return computeContextInformation2(context, proposals, true, monitor);
	}
	
	public IStatus computeContextInformation2(final AssistInvocationContext context,
			final AssistProposalCollector<?> proposals, final boolean createContextInfoOnly, final IProgressMonitor monitor) {
		if (context.getModelInfo() == null) {
			return null;
		}
		
		int offset = context.getInvocationOffset();
		IDocument document = context.getSourceViewer().getDocument();
		final RHeuristicTokenScanner scanner = getScanner();
		int indexShift = 0;
		if (document instanceof InputDocument) {
			final InputDocument inputDoc = (InputDocument) document;
			document = inputDoc.getMasterDocument();
			indexShift = inputDoc.getOffsetInMasterDocument();
			offset += indexShift;
		}
		if (scanner == null || offset < 2) {
			return null;
		}
		scanner.configureDefaultParitions(document);
		if (scanner.getPartitioningConfig().getDefaultPartitionConstraint().matches(scanner.getPartition(offset-1).getType())) {
			final int index = scanner.findOpeningPeer(offset-1, F_BRACKETS);
			if (index >= 0) {
				final FCallInfo fcallInfo = searchFCallInfo(context, index-indexShift);
				if (fcallInfo != null) {
					doComputeFCallContextInformation(context, fcallInfo, proposals, createContextInfoOnly);
				}
			}
		}
		return Status.OK_STATUS;
	}
	
	private FCallInfo searchFCallInfo(final AssistInvocationContext context, final int fcallOpen) {
		final AstInfo astInfo = context.getAstInfo();
		if (astInfo == null || astInfo.root == null) {
			return null;
		}
		final AstSelection selection = AstSelection.search(astInfo.root, fcallOpen, fcallOpen+1, AstSelection.MODE_COVERING_SAME_LAST);
		IAstNode node = selection.getCovering();
		
		while (node != null && node instanceof RAstNode) {
			final RAstNode rnode = (RAstNode) node;
			FCall fcallNode = null;
			if (rnode.getNodeType() == NodeType.F_CALL
					&& (fcallOpen == (fcallNode = ((FCall) rnode)).getArgsOpenOffset())) {
				final Object[] attachments = fcallNode.getAttachments();
				for (int i = 0; i < attachments.length; i++) {
					if (attachments[i] instanceof RElementAccess) {
						final RElementAccess fcallAccess = (RElementAccess) attachments[i];
						if (fcallAccess.isFunctionAccess() && !fcallAccess.isWriteAccess()) {
							final FCallInfo info = new FCallInfo(fcallNode, fcallAccess);
							if (initEnvirList(context, fcallNode)) {
								return info;
							}
						}
					}
				}
			}
			node = rnode.getParent();
		}
		return null;
	}
	
	private void doComputeFCallContextInformation(final AssistInvocationContext context, final FCallInfo fcallInfo,
			final AssistProposalCollector proposals, final boolean createContextInfoOnly) {
		int distance = 0;
		final ExactFCallPattern pattern = new ExactFCallPattern(fcallInfo.access);
		final int infoOffset = fcallInfo.node.getArgsOpenOffset()+1;
		for (final Iterator<IRFrame> iter = new EnvirIter(); iter.hasNext();) {
			final IRFrame envir = iter.next();
			final List<? extends IModelElement> elements = envir.getModelChildren(null);
			for (final IModelElement element : elements) {
				final IElementName elementName = element.getElementName();
				final int c1type = (element.getElementType() & IModelElement.MASK_C1);
				if ((c1type == IModelElement.C1_METHOD)
						&& isCompletable(elementName)
						&& pattern.matches(elementName)) {
					final IRMethod method = (IRMethod) element;
					proposals.add(createContextInfoOnly ?
							new RArgumentListContextInformation(infoOffset, method) :
							new RElementCompletionProposal.ContextInformationProposal(context, method.getElementName(), infoOffset,
								method, -distance, fLabelProvider) );
				}
			}
			distance++;
		}
	}
	
	private void doComputeFCallArgumentProposals(final AssistInvocationContext context,
			final int offset, final FCallInfo fcallInfo, final String prefix,
			final AssistProposalCollector<IAssistCompletionProposal> proposals) {
		int distance = 0;
		final HashSet<String> names = new HashSet<String>();
		for (final Iterator<IRFrame> iter = new EnvirIter(); iter.hasNext();) {
			final IRFrame envir = iter.next();
			final List<? extends IModelElement> elements = envir.getModelChildren(null);
			for (final IModelElement element : elements) {
				final IElementName elementName = element.getElementName();
				final int c1type = (element.getElementType() & IModelElement.MASK_C1);
				if ((c1type == IModelElement.C1_METHOD)
						&& isCompletable(elementName)
						&& (fcallInfo.access.equals(elementName)) ) {
					final IRMethod method = (IRMethod) element;
					final ArgsDefinition argsDef = method.getArgsDefinition();
					if (argsDef != null) {
						for (int i = 0; i < argsDef.size(); i++) {
							final ArgsDefinition.Arg arg = argsDef.get(i);
							if (arg.name != null && arg.name.length() > 0 && !arg.name.equals("...")) {
								if ((prefix == null || arg.name.startsWith(prefix))
										&& names.add(arg.name)) {
									final RElementName name = RElementName.create(RElementName.MAIN_DEFAULT, arg.name);
									proposals.add(new RElementCompletionProposal.ArgumentProposal(context, name, offset, distance, fLabelProvider));
								}
							}
						}
					}
				}
			}
			distance++;
		}
	}
	
	
	private boolean initEnvirList(final AssistInvocationContext context, final RAstNode node) {
		if (fEnvirList[LOCAL_ENVIR] != null) {
			return true;
		}
		final IRFrameInSource envir = RModel.searchFrame(node);
		if (envir != null && !fCompleteRuntimeMode) {
			fEnvirList[LOCAL_ENVIR] = RModel.createDirectFrameList(envir);
		}
		else {
			fEnvirList[LOCAL_ENVIR] = new ArrayList<IRFrame>();
		}
		
		fEnvirListPackages = new HashSet<String>();
		if (!fCompleteRuntimeMode) {
			final ISourceUnit su = fEditor.getSourceUnit();
			if ((su instanceof IRSourceUnit)) {
				fEnvirList[WS_ENVIR] = RModel.createProjectFrameList(null, (IRSourceUnit) su, fEnvirListPackages);
				if (fEnvirList[WS_ENVIR] != null && !fEnvirList[WS_ENVIR].isEmpty()) {
					fEnvirList[LOCAL_ENVIR].add(fEnvirList[WS_ENVIR].remove(0));
				}
			}
		}
		if (fEnvirList[WS_ENVIR] == null) {
			fEnvirList[WS_ENVIR] = new ArrayList<IRFrame>();
		}
		addRuntimeEnvirList(context);
		return true;
	}
	
	private void addRuntimeEnvirList(final AssistInvocationContext context) {
		if (fProcess != null) {
			if (fEditor instanceof ConsolePageEditor || fCompleteRuntimeMode) {
				final RWorkspace data = fProcess.getWorkspaceData();
				final List<? extends ICombinedREnvironment> runtimeList = data.getRSearchEnvironments();
				if (runtimeList != null && !runtimeList.isEmpty()) {
					for (final ICombinedREnvironment envir : runtimeList) {
						final IRFrame frame = (IRFrame) envir;
						if (frame.getFrameType() == IRFrame.PROJECT) {
							fEnvirList[LOCAL_ENVIR].add(frame);
						}
						else {
							fEnvirList[WS_ENVIR].add(frame);
						}
					}
				}
			}
			else {
				final Set<String> requiredPackages = new HashSet<String>();
				final ISourceUnitModelInfo modelInfo = context.getModelInfo();
				if (modelInfo instanceof IRModelInfo) {
					final IRModelInfo rModel = (IRModelInfo) modelInfo;
					final IPackageReferences packages = rModel.getReferencedPackages();
					for (final String name : packages.getAllPackageNames()) {
						if (packages.isImported(name)) {
							requiredPackages.add(name);
						}
					}
				}
				requiredPackages.add("base");
				
				final RWorkspace data = fProcess.getWorkspaceData();
				final List<? extends ICombinedREnvironment> runtimeList = data.getRSearchEnvironments();
				if (runtimeList != null && !runtimeList.isEmpty()) {
					for (final ICombinedREnvironment envir : runtimeList) {
						final IRFrame frame = (IRFrame) envir;
						if (frame.getFrameType() == IRFrame.PACKAGE
								&& requiredPackages.contains(frame.getElementName().getSegmentName())
								&& !fEnvirListPackages.contains(frame.getElementName().getSegmentName())) {
							fEnvirList[RUNTIME_ENVIR].add(frame);
						}
					}
				}
			}
		}
	}
	
}
