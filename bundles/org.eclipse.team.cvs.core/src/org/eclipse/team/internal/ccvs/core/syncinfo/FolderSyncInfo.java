package org.eclipse.team.internal.ccvs.core.syncinfo;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
 
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.CVSTag;
import org.eclipse.team.internal.ccvs.core.resources.*;
import org.eclipse.team.internal.ccvs.core.util.Assert;
import org.eclipse.team.internal.ccvs.core.Policy;

/**
 * Value (immutable) object that represents workspace state information about the contents of a
 * folder that was retreived from a CVS repository. It is a specialized representation of the files from
 * the CVS sub-directory that contain folder specific connection information (e.g. Root, Repository, Tag).
 *  
 * @see ICVSFolder#getFolderSyncInfo()
 */
public class FolderSyncInfo {

	// The Repository value for virtual directories (i.e. local with no corresponding remote)
	public static final String VIRTUAL_DIRECTORY = "CVSROOT/Emptydir"; //$NON-NLS-1$

	// relative path of this folder in the repository, project1/folder1/folder2
	private String repository;
	
	// :pserver:user@host:/home/user/repo
	private String root;
	
	// sticky tag (e.g. version, date, or branch tag applied to folder)
	private CVSEntryLineTag tag;
	
	// if true then it means only part of the folder was fetched from the repository, and CVS will not create 
	// additional files in that folder.
	private boolean isStatic;

	/**
	 * Construct a folder sync object.
	 * 
	 * @param repo the relative path of this folder in the repository, cannot be <code>null</code>.
	 * @param root the location of the repository, cannot be <code>null</code>.
	 * @param tag the tag set for the folder or <code>null</code> if there is no tag applied.
	 * @param isStatic to indicate is only part of the folder was fetched from the server.
	 */
	public FolderSyncInfo(String repo, String root, CVSTag tag, boolean isStatic) {
		Assert.isNotNull(repo);
		Assert.isNotNull(root);
		this.repository = repo;
		this.root = root;
		ensureRepositoryRelativeToRoot();
		this.isStatic = isStatic;
		setTag(tag);
	}

	/**
	 * Method ensureRepositoryRelativeToRoot.
	 */
	private void ensureRepositoryRelativeToRoot() {
		String rootDir;
		try {
			rootDir = getRootDirectory();
		} catch (CVSException e) {
			// Ignore the for now. Using the root will show the error to the user.
			return;
		}
		if (repository.startsWith(rootDir)) {
			repository = repository.substring(rootDir.length());
			if (repository.startsWith(ResourceSyncInfo.SEPARATOR)) {
				repository = repository.substring(ResourceSyncInfo.SEPARATOR.length());
			}
		}
	}
	
	public boolean equals(Object other) {
		if(other == this) return true;
		if (!(other instanceof FolderSyncInfo)) return false;
			
		FolderSyncInfo syncInfo = ((FolderSyncInfo)other);
		if (!getRoot().equals(syncInfo.getRoot())) return false;
		if (!getRepository().equals(syncInfo.getRepository())) return false;
		if (getIsStatic() != syncInfo.getIsStatic()) return false;
		if ((getTag() == null) || (syncInfo.getTag() == null)) {
			if ((getTag() == null) && (syncInfo.getTag() != null) && (syncInfo.getTag().getType() != CVSTag.HEAD)) {
				return false;
			} else if ((syncInfo.getTag() == null) && (getTag() != null) && (getTag().getType() != CVSTag.HEAD)) {
				return false;
			}
		} else if (!getTag().equals(syncInfo.getTag())) {
			return false;
		}
		return true;
	}
	/**
	 * Gets the root, cannot be <code>null.
	 * 
	 * @return Returns a String
	 */
	public String getRoot() {
		return root;
	}

	/**
	 * Answer the directory portion of the root. For example, if
	 *    root = :pserver:user@host:/home/user/repo
	 * then /home/user/repo is return.
	 * <p>
	 * The root does not neccesarily contain a user name, in which cas the format is
	 * :pserver:host:/home/user/repo.
	 *
	 * 
	 * @return String
	 */
	private String getRootDirectory() throws CVSException {
		try {
			String root = getRoot();
			int index = root.indexOf('@');
			if (index == -1) {
				// If the username is mising, we have to find the third ':'.
				index = root.indexOf(':');
				if (index == 0) {
					// The method is optional so if it's not there, there is only one ':'
					index = root.indexOf(':', index + 1);
					index = root.indexOf(':', index + 1);
				}
			} else {
				// If the username was there, we find the first ':' past the '@'
				index = root.indexOf(':', index + 1);
			}
			index++;
			// strip off a leading port if there is one
			char c = root.charAt(index);
			while (Character.isDigit(c)) {
				c = root.charAt(++index);
			}
			return root.substring(index);
		} catch (IndexOutOfBoundsException e) {
			throw new CVSException(Policy.bind("FolderSyncInfo_Maleformed_root_4")); //$NON-NLS-1$
		}
	}
	
	/**
	 * Gets the tag, may be <code>null</code>.
	 * 
	 * @return Returns a String
	 */
	public CVSEntryLineTag getTag() {
		return tag;
	}

	/**
	 * Gets the repository, may be <code>null</code>.
	 * 
	 * @return Returns a String
	 */
	public String getRepository() {
		return repository;
	}

	/**
	 * Gets the isStatic.
	 * 
	 * @return Returns a boolean
	 */
	public boolean getIsStatic() {
		return isStatic;
	}

	/**
	 * Answers a full path to the folder on the remote server. This by appending the repository to the
	 * repository location speficied in the root.
	 * 
	 * Example:
	 * 	root = :pserver:user@host:/home/user/repo
	 * 	repository = folder1/folder2
	 * 
	 * Returns:
	 * 	/home/users/repo/folder1/folder2
	 * 
	 * Note: CVS supports repository root directories that end in a slash (/).
	 * For these directories, the remote location must contain two slashes (//)
	 * between the root directory and the rest of the path. For example:
	 * 	root = :pserver:user@host:/home/user/repo/
	 * 	repository = folder1/folder2
	 * must return:
	 * 	/home/users/repo//folder1/folder2
	 * 
	 * @return the full path of this folder on the server.
	 * @throws a CVSException if the root or repository is malformed.
	 */
	public String getRemoteLocation() throws CVSException {
		return getRootDirectory() + ResourceSyncInfo.SEPARATOR + getRepository(); //$NON-NLS-1$
	}
	
	/*
	 * Provide a hashCode() method that gaurentees that equal object will have the
	 * same hashCode
	 */
	public int hashCode() {
		return getRoot().hashCode() | getRepository().hashCode();
	}
	
	/**
	 * Sets the tag for the folder.
	 * 
	 * @param tag The tag to set
	 */
	private void setTag(CVSTag tag) {
		if (tag == null || tag.equals(CVSTag.DEFAULT)) {
			this.tag = null;
		} else {
			this.tag = new CVSEntryLineTag(tag);
		}
	}
	/*
	 * @see Object#toString()
	 */
	public String toString() {
		return getRoot() + "/" + getRepository() + "/" + getTag(); //$NON-NLS-1$ //$NON-NLS-2$
	}
}
