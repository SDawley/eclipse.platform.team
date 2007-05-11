/*******************************************************************************
 * Copyright (c) 2000, 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.team.internal.ccvs.ui.subscriber;

import org.eclipse.core.resources.IResource;
import org.eclipse.jface.dialogs.*;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.team.internal.ccvs.ui.*;
import org.eclipse.team.internal.core.subscribers.ActiveChangeSet;
import org.eclipse.ui.PlatformUI;

/**
 * Dialog for creating and editing commit set
 * title and comment
 */
public class CommitSetDialog extends TitleAreaDialog {

    public final static short NEW = 0;
    public final static short EDIT = 1;
    
	private static final int DEFAULT_WIDTH_IN_CHARS= 80;
    
    private final ActiveChangeSet set;
    private CommitCommentArea commitCommentArea;
    private Text nameText;
    private Button useTitleButton;
    private Button enterCommentButton;
    private final String title;
    private final String description;
    private String comment;
    private short mode;

    public CommitSetDialog(Shell parentShell, ActiveChangeSet set, IResource[] files, short mode) {
        super(parentShell);
        this.set = set;
        this.mode = mode;
		this.title = mode == NEW ? CVSUIMessages.WorkspaceChangeSetCapability_2
				: CVSUIMessages.WorkspaceChangeSetCapability_7;
		this.description = mode == NEW ? CVSUIMessages.WorkspaceChangeSetCapability_3
				: CVSUIMessages.WorkspaceChangeSetCapability_8;
        
        if (files == null) {
            files = set.getResources();
        }
        
		int shellStyle = getShellStyle();
		setShellStyle(shellStyle | SWT.RESIZE | SWT.MAX);
		commitCommentArea = new CommitCommentArea();
		// Get a project from which the commit template can be obtained
		if (files.length > 0) 
		    commitCommentArea.setProject(files[0].getProject());
    }
    
    /* (non-Javadoc)
     * @see org.eclipse.jface.dialogs.TitleAreaDialog#createContents(org.eclipse.swt.widgets.Composite)
     */
    protected Control createContents(Composite parent) {
        Control contents = super.createContents(parent);
        setTitle(title);
        setMessage(description);
        return contents;
    }
    
    /* (non-Javadoc)
     * @see org.eclipse.jface.dialogs.Dialog#createDialogArea(org.eclipse.swt.widgets.Composite)
     */
    protected Control createDialogArea(Composite parent) {
        Composite parentComposite = (Composite) super.createDialogArea(parent);

        // create a composite with standard margins and spacing
        Composite composite = new Composite(parentComposite, SWT.NONE);
        GridLayout layout = new GridLayout();
        layout.marginHeight = convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_MARGIN);
        layout.marginWidth = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_MARGIN);
        layout.verticalSpacing = convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_SPACING);
        layout.horizontalSpacing = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_SPACING);
        composite.setLayout(layout);
        composite.setLayoutData(new GridData(GridData.FILL_BOTH));
        composite.setFont(parentComposite.getFont());
		
		createNameArea(composite);
		
		if (hasCommitTemplate()) {
		    if (set.hasComment()) {
		        // Only set the comment if the set has a custom comment.
		        // Otherwise, the template should be used
		        comment = set.getComment();
		        commitCommentArea.setProposedComment(comment);
		    }
		} else {
		    comment = set.getComment();
		    commitCommentArea.setProposedComment(comment);
		    createOptionsArea(composite);
		}
		
		commitCommentArea.createArea(composite);
		commitCommentArea.addPropertyChangeListener(new IPropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent event) {
				if (event.getProperty() == CommitCommentArea.OK_REQUESTED) {
					okPressed();
				} else if (event.getProperty() == CommitCommentArea.COMMENT_MODIFIED) {
				    comment = (String)event.getNewValue();
					updateEnablements();
				}
			}
		});
		
		initializeValues();
		updateEnablements();
		
        Dialog.applyDialogFont(parent);
        return composite;
    }
    
	/* (non-Javadoc)
	 * @see org.eclipse.jface.window.Window#getInitialSize()
	 */
	protected Point getInitialSize() {
	    final Point size= super.getInitialSize();
	    size.x= convertWidthInCharsToPixels(DEFAULT_WIDTH_IN_CHARS);
	    size.y += convertHeightInCharsToPixels(8);
	    return size;
	}

    private void createNameArea(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		layout.verticalSpacing = convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_SPACING);
		layout.horizontalSpacing = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_SPACING);
		layout.numColumns = 2;
		composite.setLayout(layout);
		composite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		composite.setFont(parent.getFont());
		
		Label label = new Label(composite, SWT.NONE);
		label.setText(CVSUIMessages.CommitSetDialog_0); 
		label.setLayoutData(new GridData(GridData.BEGINNING));
		
		nameText = new Text(composite, SWT.BORDER);
		nameText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        nameText.addModifyListener(new ModifyListener() {
            public void modifyText(ModifyEvent e) {
                updateEnablements();
            }
        });
    }

    private void initializeValues() {
        String initialText = set.getTitle();
        if (initialText == null) initialText = ""; //$NON-NLS-1$
        nameText.setText(initialText);
        nameText.setSelection(0, initialText.length());
        
        if (useTitleButton != null) {
            useTitleButton.setSelection(!set.hasComment());
            enterCommentButton.setSelection(set.hasComment());
        }
    }
    
    private void createOptionsArea(Composite composite) {
		Composite radioArea = new Composite(composite, SWT.NONE);
		RowLayout radioAreaLayout = new RowLayout(SWT.VERTICAL);
		radioAreaLayout.marginLeft = 0;
		radioAreaLayout.marginRight = 0;
		radioAreaLayout.marginTop = 0;
		radioAreaLayout.marginBottom = 0;
		radioArea.setLayout(radioAreaLayout);
		
        useTitleButton = createRadioButton(radioArea, CVSUIMessages.CommitSetDialog_2); 
        enterCommentButton = createRadioButton(radioArea, CVSUIMessages.CommitSetDialog_3); 
        SelectionAdapter listener = new SelectionAdapter() {
            public void widgetSelected(SelectionEvent e) {
                updateEnablements();
            }
        };
        useTitleButton.addSelectionListener(listener);
        enterCommentButton.addSelectionListener(listener);
        
    }
    
	private Button createRadioButton(Composite parent, String label) {
		Button button = new Button(parent, SWT.RADIO);
		button.setText(label);
		return button;
	}
	
    private void updateEnablements() {
        commitCommentArea.setEnabled(isUseCustomComment());
		setErrorMessage(null);
		String name = nameText.getText();
		if (name.length() == 0) {
			setPageComplete(false);
			return;
		}
        
		// check if the new change set already exists
		if (mode == NEW 
				&& CVSUIPlugin.getPlugin().getChangeSetManager().getSet(name) != null) {
			setPageComplete(false);
			setErrorMessage(CVSUIMessages.WorkspaceChangeSetCapability_9);
			return;
		}
		
		// check if the edited change set already exists, do not display the
		// error message when new the name is the same as the old one
		if (mode == EDIT && !name.equals(set.getName()) 
				&& CVSUIPlugin.getPlugin().getChangeSetManager().getSet(name) != null) {
			setPageComplete(false);
			setErrorMessage(CVSUIMessages.WorkspaceChangeSetCapability_9);
			return;
		}

        
        if (isUseCustomComment()) {
            if (comment == null || comment.length() == 0) {
               setPageComplete(false);
               return;
            }
        }
        setPageComplete(true);
    }
    
	final protected void setPageComplete(boolean complete) {
	    Button okButton = getButton(IDialogConstants.OK_ID);
		if(okButton != null ) {
			okButton.setEnabled(complete);
		}
	}
	
    private boolean hasCommitTemplate() {
        return commitCommentArea.hasCommitTemplate();
    }
    
    /* (non-Javadoc)
     * @see org.eclipse.jface.dialogs.Dialog#okPressed()
     */
    protected void okPressed() {
        set.setTitle(nameText.getText());
        if (isUseCustomComment()) {
            // Call getComment so the comment gets saved
            set.setComment(commitCommentArea.getComment(true));
        } else {
            set.setComment(null);
        }
        super.okPressed();
    }

    private boolean isUseCustomComment() {
        return enterCommentButton == null || enterCommentButton.getSelection();
    }

	protected Label createWrappingLabel(Composite parent, String text) {
		Label label = new Label(parent, SWT.LEFT | SWT.WRAP);
		label.setText(text);
		GridData data = new GridData();
		data.horizontalSpan = 1;
		data.horizontalAlignment = GridData.FILL;
		data.horizontalIndent = 0;
		data.grabExcessHorizontalSpace = true;
		data.widthHint = 200;
		label.setLayoutData(data);
		return label;
	}
	
	/* (non-Javadoc)
     * @see org.eclipse.jface.dialogs.Dialog#createButtonBar(org.eclipse.swt.widgets.Composite)
     */
    protected Control createButtonBar(Composite parent) {
        Control control = super.createButtonBar(parent);
        updateEnablements();
        return control;
    }
    
    /* (non-Javadoc)
	 * @see org.eclipse.jface.window.Window#configureShell(org.eclipse.swt.widgets.Shell)
	 */
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		shell.setText(title);
		// set F1 help
		PlatformUI.getWorkbench().getHelpSystem().setHelp(shell,
				IHelpContextIds.COMMIT_SET_DIALOG);

	}
}