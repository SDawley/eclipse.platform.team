/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.compare.internal;

import java.text.MessageFormat;
import java.lang.reflect.InvocationTargetException;

import org.eclipse.swt.widgets.Composite;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.Viewer;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;

import org.eclipse.compare.*;
import org.eclipse.compare.structuremergeviewer.*;


/**
 * A two-way or three-way compare for arbitrary IResources.
 */
class ResourceCompareInput extends CompareEditorInput {
	
	private static final boolean NORMALIZE_CASE= true;
	
	private boolean fThreeWay= false;
	private Object fRoot;
	private IStructureComparator fAncestor;
	private IStructureComparator fLeft;
	private IStructureComparator fRight;
	private IResource fAncestorResource;
	private IResource fLeftResource;
	private IResource fRightResource;
	private DiffTreeViewer fDiffViewer;
	private IAction fOpenAction;
	
	class MyDiffNode extends DiffNode {
		
		private boolean fDirty= false;
		private ITypedElement fLastId;
		private String fLastName;
		
		
		public MyDiffNode(IDiffContainer parent, int description, ITypedElement ancestor, ITypedElement left, ITypedElement right) {
			super(parent, description, ancestor, left, right);
		}
		public void fireChange() {
			super.fireChange();
			setDirty(true);
			fDirty= true;
			if (fDiffViewer != null)
				fDiffViewer.refresh(this);
		}
		void clearDirty() {
			fDirty= false;
		}
		public String getName() {
			if (fLastName == null)
				fLastName= super.getName();
			if (fDirty)
				return '<' + fLastName + '>';
			return fLastName;
		}
		
		public ITypedElement getId() {
			ITypedElement id= super.getId();
			if (id == null)
				return fLastId;
			fLastId= id;
			return id;
		}
	}
	
	/**
	 * Creates an compare editor input for the given selection.
	 */
	ResourceCompareInput(CompareConfiguration config) {
		super(config);
	}
			
	public Viewer createDiffViewer(Composite parent) {
		fDiffViewer= new DiffTreeViewer(parent, getCompareConfiguration()) {
			protected void fillContextMenu(IMenuManager manager) {
				
				if (fOpenAction == null) {
					fOpenAction= new Action() {
						public void run() {
							handleOpen(null);
						}
					};
					Utilities.initAction(fOpenAction, getBundle(), "action.CompareContents."); //$NON-NLS-1$
				}
				
				boolean enable= false;
				ISelection selection= getSelection();
				if (selection instanceof IStructuredSelection) {
					IStructuredSelection ss= (IStructuredSelection)selection;
					if (ss.size() == 1) {
						Object element= ss.getFirstElement();
						if (element instanceof MyDiffNode) {
							ITypedElement te= ((MyDiffNode) element).getId();
							if (te != null)
								enable= !ITypedElement.FOLDER_TYPE.equals(te.getType());
						} else
							enable= true;
					}
				}
				fOpenAction.setEnabled(enable);
				
				manager.add(fOpenAction);
				
				super.fillContextMenu(manager);
			}
		};
		return fDiffViewer;
	}

	void setSelection(ISelection s) {
		
		IResource[] selection= Utilities.getResources(s);

		fThreeWay= selection.length == 3;
		
		fAncestorResource= null;
		fLeftResource= selection[0];
		fRightResource= selection[1];
		if (fThreeWay) {
			fLeftResource= selection[1];		
			fRightResource= selection[2];
		}
		
		fAncestor= null;
		fLeft= getStructure(fLeftResource);
		fRight= getStructure(fRightResource);
					
		if (fThreeWay) {
			fAncestorResource= selection[0];
			fAncestor= getStructure(fAncestorResource);
		}
	}
	
	/**
	 * Returns true if compare can be executed for the given selection.
	 */
	public boolean isEnabled(ISelection s) {
		
		IResource[] selection= Utilities.getResources(s);
		if (selection.length < 2 || selection.length > 3)
			return false;

		fThreeWay= selection.length == 3;
		
		fLeftResource= selection[0];
		fRightResource= selection[1];
		if (fThreeWay) {
			fLeftResource= selection[1];		
			fRightResource= selection[2];
		}
							
		if (!comparable(fLeftResource, fRightResource))
			return false;

		if (fThreeWay) {
			fAncestorResource= selection[0];
			
			if (!comparable(fLeftResource, fRightResource))
				return false;
		}

		return true;
	}
	
	/**
	 * Initializes the images in the compare configuration.
	 */
	void initializeCompareConfiguration() {
		CompareConfiguration cc= getCompareConfiguration();
		if (fLeftResource != null) {
			cc.setLeftLabel(buildLabel(fLeftResource));
			cc.setLeftImage(CompareUIPlugin.getImage(fLeftResource));
		}
		if (fRightResource != null) {
			cc.setRightLabel(buildLabel(fRightResource));
			cc.setRightImage(CompareUIPlugin.getImage(fRightResource));
		}
		if (fThreeWay && fAncestorResource != null) {			
			cc.setAncestorLabel(buildLabel(fAncestorResource));
			cc.setAncestorImage(CompareUIPlugin.getImage(fAncestorResource));
		}
	}
	
	/**
	 * Returns true if both resources are either structured or unstructured.
	 */
	private boolean comparable(IResource c1, IResource c2) {
		return hasStructure(c1) == hasStructure(c2);
	}
	
	/**
	 * Returns true if the given argument has a structure.
	 */
	private boolean hasStructure(IResource input) {
		
		if (input instanceof IContainer)
			return true;
			
		if (input instanceof IFile) {
			IFile file= (IFile) input;
			String type= file.getFileExtension();
			if (type != null) {
				type= normalizeCase(type);
				return "JAR".equals(type) || "ZIP".equals(type);	//$NON-NLS-2$ //$NON-NLS-1$
			}
		}
		
		return false;
	}
	
	/**
	 * Creates a <code>IStructureComparator</code> for the given input.
	 * Returns <code>null</code> if no <code>IStructureComparator</code>
	 * can be found for the <code>IResource</code>.
	 */
	private IStructureComparator getStructure(IResource input) {
		
		if (input instanceof IContainer)
			return new BufferedResourceNode(input);
			
		if (input instanceof IFile) {
			IStructureComparator rn= new BufferedResourceNode(input);
			IFile file= (IFile) input;
			String type= normalizeCase(file.getFileExtension());
			if ("JAR".equals(type) || "ZIP".equals(type)) //$NON-NLS-2$ //$NON-NLS-1$
				return new ZipFileStructureCreator().getStructure(rn);
			return rn;
		}
		return null;
	}
	
	/**
	 * Performs a two-way or three-way diff on the current selection.
	 */
	public Object prepareInput(IProgressMonitor pm) throws InvocationTargetException {
				
		try {
			// fix for PR 1GFMLFB: ITPUI:WIN2000 - files that are out of sync with the file system appear as empty							
			fLeftResource.refreshLocal(IResource.DEPTH_INFINITE, pm);
			fRightResource.refreshLocal(IResource.DEPTH_INFINITE, pm);
			if (fThreeWay && fAncestorResource != null)
				fAncestorResource.refreshLocal(IResource.DEPTH_INFINITE, pm);
			// end fix						
				
			pm.beginTask(Utilities.getString("ResourceCompare.taskName"), IProgressMonitor.UNKNOWN); //$NON-NLS-1$

			String leftLabel= fLeftResource.getName();
			String rightLabel= fRightResource.getName();
			
			String title;
			if (fThreeWay) {			
				String format= Utilities.getString("ResourceCompare.threeWay.title"); //$NON-NLS-1$
				String ancestorLabel= fAncestorResource.getName();
				title= MessageFormat.format(format, new String[] {ancestorLabel, leftLabel, rightLabel});	
			} else {
				String format= Utilities.getString("ResourceCompare.twoWay.title"); //$NON-NLS-1$
				title= MessageFormat.format(format, new String[] {leftLabel, rightLabel});
			}
			setTitle(title);
			
			Differencer d= new Differencer() {
				protected Object visit(Object parent, int description, Object ancestor, Object left, Object right) {
					return new MyDiffNode((IDiffContainer) parent, description, (ITypedElement)ancestor, (ITypedElement)left, (ITypedElement)right);
				}
			};
			
			fRoot= d.findDifferences(fThreeWay, pm, null, fAncestor, fLeft, fRight);
			return fRoot;
			
		} catch (CoreException ex) {
			throw new InvocationTargetException(ex);
		} finally {
			pm.done();
		}
	}
	
	public String getToolTipText() {
		if (fLeftResource != null && fRightResource != null) {
			String leftLabel= fLeftResource.getFullPath().makeRelative().toString();
			String rightLabel= fRightResource.getFullPath().makeRelative().toString();			
			if (fThreeWay) {			
				String format= Utilities.getString("ResourceCompare.threeWay.tooltip"); //$NON-NLS-1$
				String ancestorLabel= fAncestorResource.getFullPath().makeRelative().toString();
				return MessageFormat.format(format, new String[] {ancestorLabel, leftLabel, rightLabel});
			} else {
				String format= Utilities.getString("ResourceCompare.twoWay.tooltip"); //$NON-NLS-1$
				return MessageFormat.format(format, new String[] {leftLabel, rightLabel});
			}
		}
		// fall back
		return super.getToolTipText();
	}
	
	private String buildLabel(IResource r) {
		String n= r.getFullPath().toString();
		if (n.charAt(0) == IPath.SEPARATOR)
			return n.substring(1);
		return n;
	}
	
	public void saveChanges(IProgressMonitor pm) throws CoreException {
		super.saveChanges(pm);
		if (fRoot instanceof DiffNode) {
			try {
				commit(pm, (DiffNode) fRoot);
			} finally {
				if (fDiffViewer != null)
					fDiffViewer.refresh();				
				setDirty(false);
			}
		}
	}
	
	/*
	 * Recursively walks the diff tree and commits all changes.
	 */
	private static void commit(IProgressMonitor pm, DiffNode node) throws CoreException {
		
		if (node instanceof MyDiffNode)		
			((MyDiffNode)node).clearDirty();
		
		ITypedElement left= node.getLeft();
		if (left instanceof BufferedResourceNode)
			((BufferedResourceNode) left).commit(pm);
			
		ITypedElement right= node.getRight();
		if (right instanceof BufferedResourceNode)
			((BufferedResourceNode) right).commit(pm);

		IDiffElement[] children= node.getChildren();
		if (children != null) {
			for (int i= 0; i < children.length; i++) {
				IDiffElement element= children[i];
				if (element instanceof DiffNode)
					commit(pm, (DiffNode) element);
			}
		}
	}
	
	private static String normalizeCase(String s) {
		if (NORMALIZE_CASE && s != null)
			return s.toUpperCase();
		return s;
	}
}

