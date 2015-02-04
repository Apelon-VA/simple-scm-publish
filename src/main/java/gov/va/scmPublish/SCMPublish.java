/**
 * Copyright Notice
 *
 * This is a work of the U.S. Government and is not subject to copyright 
 * protection in the United States. Foreign copyrights may apply.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package gov.va.scmPublish;

import gov.va.isaac.interfaces.sync.MergeFailOption;
import gov.va.isaac.interfaces.sync.MergeFailure;
import gov.va.isaac.interfaces.sync.ProfileSyncI;
import gov.va.isaac.sync.git.SyncServiceGIT;
import gov.va.isaac.sync.svn.SyncServiceSVN;
import java.io.BufferedReader;
import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.naming.AuthenticationException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * {@link SCMPublish}
 * A class that makes what should be a simple task (checkout a repo, update some files, commit back the changes) 
 * simple within maven, for either GIT or SVN.  This is required because the maven-scm-plugin is TERRIBLE.  Quite 
 * frankly, I'm simply sick and tired of dealing with it.
 * 
 * This operates as a single execution.  You pass it a folder that you want synced - and the remote repository URL.
 * It will check out the repo - copy over the changed files from the folder you specified, and then commit the changes.
 * 
 * New files are handled automatically.  Deletes are not yet handled (but could be with a little work when the need arises)
 * 
 * This allows authentication to be passed in via system property, parameter, or, will 
 * prompt for the username/password (if allowed by the system property 'scmPublishNoPrompt')
 * IN THAT ORDER.  System properties have the highest priority.
 * 
 * To prevent prompting during automated runs - set the system property 'scmPublishNoPrompt=true'
 * To set the username via system property - set 'scmPublishUsername=username'
 * To set the password via system property - set 'scmPublishPassword=password'
 * 
 * To enable authentication without prompts, using public keys - set both of the following 
 *   'scmPublishUsername=username'
 *   'scmPublishNoPrompt=true' 
 *   
 * This will cause a public key authentication to be attempted using the ssh credentials found 
 * in the current users .ssh folder (in their home directory)
 *
 * @author <a href="mailto:daniel.armbrust.list@gmail.com">Dan Armbrust</a> 
 */
@Mojo( name = "simple-scm-publish", defaultPhase = LifecyclePhase.DEPLOY)
public class SCMPublish extends AbstractMojo
{
	// For disabling Profile Sync entirely
	public static final String SCM_PUBLISH_DISABLE = "scmPublishDisable";
	
	// For preventing command line prompts for credentials during automated runs - set this system property to true.
	public static final String SCM_PUBLISH_NO_PROMPTS = "scmPublishNoPrompt";
	
	// Allow setting the username via a system property
	public static final String SCM_PUBLISH_USERNAME_PROPERTY = "scmPublishUsername";
	
	// Allow setting the password via a system property
	public static final String SCM_PUBLISH_PASSWORD_PROPERTY = "scmPublishPassword";
	
	private boolean disableHintGiven = false;
	private static String username = null;
	private static String password = null;
	
	/**
	 * The folder to use for the checkout, update, and commit - defaults to project.build.directory/scmPublish
	 */
	@Parameter( required = true, defaultValue = "${project.build.directory}/scmPublish" )
	protected File workingFolder;
	
	/**
	 * The folder that contains the content that should be published
	 */
	@Parameter( required = true )
	protected File contentFolder;
	
	/**
	 * If provided, only copy files with the given extension - '.pdf' for example.
	 * Processed case-insensitive - otherwise, copies all files
	 */
	@Parameter( required = false )
	protected Set<String> fileExtensionsToCopy;
	
	/**
	 * The commit message to use
	 */
	@Parameter( required = false, defaultValue="[SCMPublish Plugin]")
	protected String commitMessage;
	
	/**
	 * The SCM type - either SVN or GIT
	 */
	@Parameter( required = true )
	protected String scmType;
	
	/**
	 * The SCM URL
	 */
	@Parameter( required = true )
	protected String scmURL;
	
	/**
	 * The username to use for remote operations
	 */
	@Parameter (required = false )
	private String profileSyncUsername = null;
	
	/**
	 * The password to use for remote operations
	 */
	@Parameter (required = false )
	private String profileSyncPassword = null;

	/**
	 * @see org.apache.maven.plugin.Mojo#execute()
	 */
	@Override
	public void execute() throws MojoExecutionException, MojoFailureException
	{
		try
		{
			getLog().info("Configuring " +workingFolder.getAbsolutePath() + " for SCM management");
			
			workingFolder.mkdirs();
			ProfileSyncI sync = getProfileSyncImpl();
			sync.setReadmeFileContent("This repository is used for publishing content programmatically");
			sync.linkAndFetchFromRemote(getURL(), username, password);
			
			getLog().info("Copying contents from " + contentFolder.getAbsolutePath());
			getLog().info("Copied " + FolderCopy.copy(contentFolder.toPath(), workingFolder.toPath(), false, fileExtensionsToCopy) + " files");
			
			getLog().info("Pushing new content");
			sync.addUntrackedFiles();
			sync.updateCommitAndPush(commitMessage, username, password, MergeFailOption.FAIL, (String[])null);
			
			getLog().info("SCM publish complete");
		}
		catch (AuthenticationException | IllegalArgumentException | IOException | MergeFailure e)
		{
			throw new MojoExecutionException("Failed: " + e.toString(), e);
		}
	}
	
	
	//TODO lots of copy-paste code here - need to remerge it back with ProfileBaseMojo after a refactoring
	
	protected ProfileSyncI getProfileSyncImpl() throws MojoExecutionException
	{
		if (scmType.equalsIgnoreCase("GIT"))  //TODO move these constants somewhere useful
		{
			return new SyncServiceGIT(workingFolder);
		}
		else if (scmType.equalsIgnoreCase("SVN"))
		{
			return new SyncServiceSVN(workingFolder);
		}
		else
		{
			throw new MojoExecutionException("Unsupported change set URL Type");
		}
	}
	
	/**
	 * Does the necessary substitution to put the contents of getUserName() into the URL, if a known pattern needing substitution is found.
	 *  ssh://someuser@csfe.aceworkspace.net:29418/... for example needs to become:
	 *  ssh://<getUsername()>@csfe.aceworkspace.net:29418/...
	 * @throws MojoExecutionException 
	 */
	protected String getURL() throws MojoExecutionException
	{
		return getProfileSyncImpl().substituteURL(scmURL, getUsername());
	}
	
	protected String getUsername() throws MojoExecutionException
	{
		if (username == null)
		{
			username = System.getProperty(SCM_PUBLISH_USERNAME_PROPERTY);
			
			//still blank, try property
			if (username == null || username.length() == 0)
			{
				username = profileSyncUsername;
			}
			
			//still no username, prompt if allowed
			if ((username == null || username.length() == 0) && !Boolean.getBoolean(SCM_PUBLISH_NO_PROMPTS))
			{
				Callable<Void> callable = new Callable<Void>()
				{
					@Override
					public Void call() throws Exception
					{
						if (!disableHintGiven)
						{
							System.out.println("To disable remote sync during build, add '-D" + SCM_PUBLISH_DISABLE + "=true' to your maven command");
							disableHintGiven = true;
						}
						
						try
						{
							System.out.println("Enter the " + scmType + " username for the Profiles/Changset remote store (" +
									scmURL + "):");
							BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
							username = br.readLine();
						}
						catch (IOException e)
						{
							throw new MojoExecutionException("Error reading username from console");
						}
						return null;
					}
				};
				
				try
				{
					Executors.newSingleThreadExecutor(new ThreadFactory()
					{
						@Override
						public Thread newThread(Runnable r)
						{
							Thread t = new Thread(r, "User Prompt Thread");
							t.setDaemon(true);
							return t;
						}
					}).submit(callable).get(2, TimeUnit.MINUTES);
				}
				catch (TimeoutException | InterruptedException e)
				{
					throw new MojoExecutionException("Username not provided within timeout");
				}
				catch (ExecutionException ee)
				{
					throw (ee.getCause() instanceof MojoExecutionException ? (MojoExecutionException)ee.getCause() : 
						new MojoExecutionException("Unexpected", ee.getCause()));
				}
			}
		}
		return username;
	}
	
	protected String getPassword() throws MojoExecutionException
	{
		if (password == null)
		{
			password = System.getProperty(SCM_PUBLISH_PASSWORD_PROPERTY);
			
			//still blank, try the passed in param
			if ((password == null || username.length() == 0))
			{
				password = profileSyncPassword;
			}
			
			//still no password, prompt if allowed
			if ((password == null || username.length() == 0) && !Boolean.getBoolean(SCM_PUBLISH_NO_PROMPTS))
			{
				Callable<Void> callable = new Callable<Void>()
				{
					@Override
					public Void call() throws Exception
					{
						try
						{
							if (!disableHintGiven)
							{
								System.out.println("To disable remote sync during build, add '-D" + SCM_PUBLISH_DISABLE + "=true' to your maven command");
								disableHintGiven = true;
							}
							System.out.println("Enter the " + scmType + " password for the Profiles/Changset remote store: (" +
									scmURL + "):");
							
							//Use console if available, for password masking
							Console console = System.console();
							if (console != null)
							{
								password = new String(console.readPassword());
							}
							else
							{
								BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
								password = br.readLine();
							}
						}
						catch (IOException e)
						{
							throw new MojoExecutionException("Error reading password from console");
						}
						return null;
					}
				};
				
				try
				{
					Executors.newSingleThreadExecutor(new ThreadFactory()
					{
						@Override
						public Thread newThread(Runnable r)
						{
							Thread t = new Thread(r, "User Password Prompt Thread");
							t.setDaemon(true);
							return t;
						}
					}).submit(callable).get(2, TimeUnit.MINUTES);
				}
				catch (TimeoutException | InterruptedException e)
				{
					throw new MojoExecutionException("Password not provided within timeout");
				}
				catch (ExecutionException ee)
				{
					throw (ee.getCause() instanceof MojoExecutionException ? (MojoExecutionException)ee.getCause() : 
						new MojoExecutionException("Unexpected", ee.getCause()));
				}
			}
		}
		return password;
	}
}
