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
package org.eclipse.team.ui.synchronize.viewers;

import java.util.*;

import org.eclipse.compare.structuremergeviewer.*;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.viewers.AbstractTreeViewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.widgets.Control;
import org.eclipse.team.core.ITeamStatus;
import org.eclipse.team.core.synchronize.*;
import org.eclipse.team.internal.core.TeamPlugin;
import org.eclipse.team.internal.ui.TeamUIPlugin;

/**
 * An input that can be used with both {@link DiffTreeViewerConfiguration} and 
 * {@link CompareEditorInput}. The
 * job of this input is to create the logical model of the contents of the
 * sync set for displaying to the user. The created logical model must diff
 * nodes.
 * <p>
 * 1. First, prepareInput is called to initialize the model with the given sync
 * set. Building the model occurs in the ui thread.
 * 2. The input must react to changes in the sync set and adjust its diff node
 * model then update the viewer. In effect mediating between the sync set
 * changes and the model shown to the user. This happens in the ui thread.
 * </p>
 * 
 * @since 3.0
 */
public class SyncInfoSetViewerInput extends SyncInfoDiffNode implements ISyncInfoSetChangeListener {

	// During updates we keep track of the parent elements that need their
	// labels updated. This is required to support displaying information in a 
	// parent label that is dependant on the state of its children. For example,
	// showing conflict markers on folders if it contains child conflicts.
	private Set parentsToUpdate = new HashSet();
	// Map from resources to model objects. This allows effecient lookup
	// of model objects based on changes occuring to resources.
	private Map resourceMap = Collections.synchronizedMap(new HashMap());
	// The viewer this input is being displayed in
	private AbstractTreeViewer viewer;
	// Flasg to indicate if tree control should be updated while
	// building the model.
	private boolean refreshViewer;

	/**
	 * Create an input based on the provide sync set. The input is not initialized
	 * until <code>prepareInput</code> is called. 
	 * 
	 * @param set the sync set used as the basis for the model created by this input.
	 */
	public SyncInfoSetViewerInput(SyncInfoTree set) {
		super(null /* no parent */, set, ResourcesPlugin.getWorkspace().getRoot());
	}

	/**
	 * Return the model object (i.e. an instance of <code>SyncInfoDiffNode</code>
	 * or one of its subclasses) for the given IResource.
	 * @param resource
	 *            the resource
	 * @return the <code>SyncInfoDiffNode</code> for the given resource
	 */
	protected DiffNode getModelObject(IResource resource) {
		return (DiffNode) resourceMap.get(resource);
	}

	/**
	 * Return the <code>AbstractTreeViewer</code> asociated with this content
	 * provider or <code>null</code> if the viewer is not of the proper type.
	 * @return
	 */
	public AbstractTreeViewer getTreeViewer() {
		return viewer;
	}

	public void setViewer(AbstractTreeViewer viewer) {
		this.viewer = viewer;
	}

	public ViewerSorter getViewerSorter() {
		return new SyncInfoDiffNodeSorter();
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.compare.structuremergeviewer.DiffContainer#hasChildren()
	 */
	public boolean hasChildren() {
		// This is required to allow the sync framework to be used in wizards
		// where the input is not populated until after the compare input is
		// created
		// (i.e. the compare input will only create the diff viewer if the
		// input has children
		return true;
	}

	/**
	 * Builds the viewer model based on the contents of the sync set.
	 */
	public void prepareInput(IProgressMonitor monitor) {
		try {
			// Connect to the sync set which will register us as a listener and give us a reset event
			// in a background thread
			getSyncInfoTree().connect(this, monitor);
		} catch (CoreException e) {
			// Shouldn't happen
			TeamPlugin.log(e);
		}
	}
	
	/**
	 * Dispose of the builder
	 */
	public void dispose() {
		resourceMap.clear();
		getSyncInfoTree().removeSyncSetChangedListener(this);
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.team.ccvs.syncviews.views.ISyncSetChangedListener#syncSetChanged()
	 */
	public void syncInfoChanged(final ISyncInfoSetChangeEvent event, IProgressMonitor monitor) {
		if (! (event instanceof ISyncInfoTreeChangeEvent)) {
			reset();
		} else {
			final Control ctrl = viewer.getControl();
			if (ctrl != null && !ctrl.isDisposed()) {
				ctrl.getDisplay().syncExec(new Runnable() {

					public void run() {
						if (!ctrl.isDisposed()) {
							BusyIndicator.showWhile(ctrl.getDisplay(), new Runnable() {

								public void run() {
									handleChanges((ISyncInfoTreeChangeEvent)event);
								}
							});
						}
					}
				});
			}
		}
	}

	/**
	 * For each node create children based on the contents of
	 * @param node
	 * @return
	 */
	protected IDiffElement[] buildModelObjects(DiffNode node) {
		IDiffElement[] children = createModelObjects(node);
		for (int i = 0; i < children.length; i++) {
			IDiffElement element = children[i];
			if (element instanceof DiffNode) {
				buildModelObjects((DiffNode) element);
			}
		}
		return children;
	}

	/**
	 * Create the
	 * @param container
	 * @return
	 */
	protected IDiffElement[] createModelObjects(DiffNode container) {
		if (container instanceof SyncInfoDiffNode) {
			SyncInfoDiffNode parentNode = (SyncInfoDiffNode) container;
			IResource resource = parentNode.getResource();
			if (resource == null) {
				resource = ResourcesPlugin.getWorkspace().getRoot();
			}
			IResource[] children = parentNode.getSyncInfoTree().members(resource);
			SyncInfoDiffNode[] nodes = new SyncInfoDiffNode[children.length];
			for (int i = 0; i < children.length; i++) {
				nodes[i] = createModelObject(parentNode, children[i]);
			}
			return nodes;
		}
		return new IDiffElement[0];
	}

	protected SyncInfoDiffNode createModelObject(DiffNode parent, IResource resource) {
		SyncInfoTree set = parent instanceof SyncInfoDiffNode ? ((SyncInfoDiffNode) parent).getSyncInfoTree() : getSyncInfoTree();
		SyncInfoDiffNode node = new SyncInfoDiffNode(parent, set, resource);
		addToViewer(node);
		return node;
	}

	/**
	 * Clear the model objects from the diff tree, cleaning up any cached state
	 * (such as resource to model object map). This method recurses deeply on
	 * the tree to allow the cleanup of any cached state for the children as
	 * well.
	 * @param node
	 *            the root node
	 */
	protected void clearModelObjects(DiffNode node) {
		IDiffElement[] children = node.getChildren();
		IResource resource = getResource(node);
		for (int i = 0; i < children.length; i++) {
			IDiffElement element = children[i];
			if (element instanceof DiffNode) {
				clearModelObjects((DiffNode) element);
			}
		}
		if (resource != null) {
			unassociateDiffNode(resource);
		}
		IDiffContainer parent = node.getParent();
		if (parent != null) {
			parent.removeToRoot(node);
		}
	}

	/**
	 * Invokes <code>getModelObject(Object)</code> on an array of resources.
	 * @param resources
	 *            the resources
	 * @return the model objects for the resources
	 */
	protected Object[] getModelObjects(IResource[] resources) {
		Object[] result = new Object[resources.length];
		for (int i = 0; i < resources.length; i++) {
			result[i] = getModelObject(resources[i]);
		}
		return result;
	}

	protected void associateDiffNode(IResource childResource, SyncInfoDiffNode childNode) {
		resourceMap.put(childResource, childNode);
	}

	protected void unassociateDiffNode(IResource childResource) {
		resourceMap.remove(childResource);
	}

	/**
	 * Return the resource associated with the node or <code>null</code> if
	 * the node is not directly associated with a resource.
	 * @param node
	 *            a diff node
	 * @return a resource or <code>null</code>
	 */
	protected IResource getResource(DiffNode node) {
		if (node instanceof SyncInfoDiffNode) {
			return ((SyncInfoDiffNode) node).getResource();
		}
		return null;
	}

	/**
	 * Handle the changes made to the viewer's <code>SyncInfoSet</code>.
	 * This method delegates the changes to the three methods <code>handleResourceChanges(ISyncInfoSetChangeEvent)</code>,
	 * <code>handleResourceRemovals(ISyncInfoSetChangeEvent)</code> and
	 * <code>handleResourceAdditions(ISyncInfoSetChangeEvent)</code>.
	 * @param event
	 *            the event containing the changed resourcses.
	 */
	protected void handleChanges(ISyncInfoTreeChangeEvent event) {
		try {
			viewer.getControl().setRedraw(false);
			handleResourceChanges(event);
			handleResourceRemovals(event);
			handleResourceAdditions(event);
			updateParentLabels();
		} finally {
			viewer.getControl().setRedraw(true);
		}
	}

	/**
	 * Update the viewer for the sync set additions in the provided event. This
	 * method is invoked by <code>handleChanges(ISyncInfoSetChangeEvent)</code>.
	 * Subclasses may override.
	 * @param event
	 */
	protected void handleResourceAdditions(ISyncInfoTreeChangeEvent event) {
		IResource[] added = event.getAddedSubtreeRoots();
		for (int i = 0; i < added.length; i++) {
			IResource resource = added[i];
			DiffNode node = getModelObject(resource);
			if (node != null) {
				// Somehow the node exists. Remove it and read it to ensure
				// what is shown matches the contents of the sync set
				removeFromViewer(resource);
			}
			// Build the sub-tree rooted at this node
			DiffNode parent = getModelObject(resource.getParent());
			if (parent != null) {
				node = createModelObject(parent, resource);
				buildModelObjects(node);
			}
		}
	}

	/**
	 * Update the viewer for the sync set changes in the provided event. This
	 * method is invoked by <code>handleChanges(ISyncInfoSetChangeEvent)</code>.
	 * Subclasses may override.
	 * @param event
	 */
	protected void handleResourceChanges(ISyncInfoTreeChangeEvent event) {
		// Refresh the viewer for each changed resource
		SyncInfo[] infos = event.getChangedResources();
		for (int i = 0; i < infos.length; i++) {
			IResource local = infos[i].getLocal();
			DiffNode diffNode = getModelObject(local);
			if (diffNode != null) {
				refreshInViewer(diffNode);
			}
		}
	}

	/**
	 * Update the viewer for the sync set removals in the provided event. This
	 * method is invoked by <code>handleChanges(ISyncInfoSetChangeEvent)</code>.
	 * Subclasses may override.
	 * @param event
	 */
	protected void handleResourceRemovals(ISyncInfoTreeChangeEvent event) {
		IResource[] removedRoots = event.getRemovedSubtreeRoots();
		if (removedRoots.length == 0)
			return;
		DiffNode[] nodes = new DiffNode[removedRoots.length];
		for (int i = 0; i < removedRoots.length; i++) {
			removeFromViewer(removedRoots[i]);
		}
	}

	protected void reset() {
		try {
			refreshViewer = false;
			resourceMap.clear();
			clearModelObjects(this);
			// remove all from tree viewer
			IDiffElement[] elements = getChildren();
			for (int i = 0; i < elements.length; i++) {
				viewer.remove(elements[i]);
			}
			associateDiffNode(ResourcesPlugin.getWorkspace().getRoot(), this);
			buildModelObjects(this);
		} finally {
			refreshViewer = true;
		}
		TeamUIPlugin.getStandardDisplay().asyncExec(new Runnable() {

			public void run() {
				if (viewer != null && !viewer.getControl().isDisposed()) {
					viewer.refresh();
				}
			}
		});
	}

	protected void refreshInViewer(DiffNode diffNode) {
		if (canUpdateViewer()) {
			AbstractTreeViewer tree = getTreeViewer();
			viewer.refresh(diffNode, true);
			updateParentLabels(diffNode);
		}
	}

	/**
	 * Remove any traces of the resource and any of it's descendants in the
	 * hiearchy defined by the content provider from the content provider and
	 * the viewer it is associated with.
	 * @param resource
	 */
	protected void removeFromViewer(IResource resource) {
		DiffNode node = getModelObject(resource);
		clearModelObjects(node);
		if (canUpdateViewer()) {
			AbstractTreeViewer tree = getTreeViewer();
			tree.remove(node);
			updateParentLabels(node);
		}
	}

	protected void addToViewer(SyncInfoDiffNode node) {
		associateDiffNode(node.getResource(), node);
		if (canUpdateViewer()) {
			AbstractTreeViewer tree = getTreeViewer();
			tree.add(node.getParent(), node);
			updateParentLabels(node);
		}
	}

	/**
	 * @param tree
	 * @return
	 */
	private boolean canUpdateViewer() {
		return refreshViewer && getTreeViewer() != null;
	}

	/**
	 * Forces the viewer to update the labels for parents whose children have
	 * changed during this round of sync set changes.
	 */
	protected void updateParentLabels() {
		try {
			if (canUpdateViewer()) {
				AbstractTreeViewer tree = getTreeViewer();
				tree.update(parentsToUpdate.toArray(new Object[parentsToUpdate.size()]), null);
			}
		} finally {
			parentsToUpdate.clear();
		}
	}

	/**
	 * Forces the viewer to update the labels for parents of this element. This
	 * can be useful when parents labels include information about their
	 * children that needs updating when a child changes.
	 * <p>
	 * This method should only be called while processing sync set changes.
	 * Changed parents are accumulated and updated at the end of the change
	 * processing
	 */
	protected void updateParentLabels(DiffNode diffNode) {
		IDiffContainer parent = diffNode.getParent();
		while (parent != null) {
			parentsToUpdate.add(parent);
			parent = parent.getParent();
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.core.subscribers.ISyncInfoSetChangeListener#syncInfoSetReset(org.eclipse.team.core.subscribers.SyncInfoSet, org.eclipse.core.runtime.IProgressMonitor)
	 */
	public void syncInfoSetReset(SyncInfoSet set, IProgressMonitor monitor) {
		reset();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.core.subscribers.ISyncInfoSetChangeListener#syncInfoSetError(org.eclipse.team.core.subscribers.SyncInfoSet, org.eclipse.team.core.ITeamStatus[], org.eclipse.core.runtime.IProgressMonitor)
	 */
	public void syncInfoSetErrors(SyncInfoSet set, ITeamStatus[] errors, IProgressMonitor monitor) {
		// TODO Auto-generated method stub
	}
}
