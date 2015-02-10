Simple SCM Publish
==================

A Maven Plugin extension that vastly simplifies the task of publishing the contents of a folder to a GIT or SVN repository.

This plugin makes what should be a simple task (checkout a repo, update some files, commit back the changes) 
simple within maven, for either GIT or SVN.  This is required because the maven-scm-plugin is TERRIBLE, and full of bugs.

For example, due to bugs in Maven, you cannot control the execution order of goals provided by the plugin within a single phase.
Which means the only way you can interleave SCM commands with other tasks like copying a file - is to put each of them in a different
phase.

Also, the configuration of the maven-scm-plugin is complex, and terribly documented.  Basic operations like add "just the new files" are
ridiculous to configure.

This plugin currently operates as a single execution.  You pass it a folder that you want synced - and the remote repository URL.
It will check out the repo - recursively copy over the changed files from the folder you specified, and then commit the changes.

New files are handled automatically.  Deletes are not yet handled (but could be with a little work when the need arises)

This allows authentication to be passed in via system property, parameter, or, will prompt for the username/password (if allowed by 
the system property 'scmPublishNoPrompt') IN THAT ORDER.  System properties have the highest priority.

- To prevent prompting during automated runs - set the system property 'scmPublishNoPrompt=true'
- To set the username via system property - set 'scmPublishUsername=username'
- To set the password via system property - set 'scmPublishPassword=password'

- To enable authentication without prompts, using public keys - set both of the following 
  - 'scmPublishUsername=username'
  - 'scmPublishNoPrompt=true' 

This will cause a public key authentication to be attempted using the ssh credentials found in the current users .ssh folder (in 
their home directory)

GOALS
=====

Currently, the only goal is 'simple-scm-publish'

Configuration
=============

##Required Configuration Parameters


contentFolder - The folder that contains the content that should be published

scmType - either SVN or GIT

scmURL - the URL for the SVN or GIT repository


##Optional Configuration Parameters
	
workingFolder - The folder to use for the checkout, update, and commit - defaults to project.build.directory/scmPublish

fileExtensionsToCopy - If provided, only copy files with the given extension - '.pdf' for example.
Processed case-insensitive - otherwise, copies all files

commitMessage - The commit message to use - defaults to [SCMPublish Plugin]

profileSyncUsername

profileSyncPassword



Release Notes
=============
mvn jgitflow:release-start jgitflow:release-finish -DdevelopmentVersion=1.1-SNAPSHOT -DreleaseVersion=1.0 -DaltDeploymentRepository=maestro::default::https://va.maestrodev.com/archiva/repository/va-releases -DdefaultOriginUrl=https://github.com/Apelon-VA/simple-scm-publish.git
