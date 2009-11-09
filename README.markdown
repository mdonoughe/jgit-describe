JGit-Describe
=============
I was setting up a buildbot for a Java project and I wanted to have a storing every built jar file, and I wanted to use git describe, so I ended up writing an ant plugin that emulates git-describe using jgit.

Building
--------
Download a copy of the jgit source and drop it into the jgit-describe folder. It should be called jgit-describe/jgit. Run ant. A jar file should appear in subfolder named dist.

Usage
-----
Add a taskdef like the following to your build.xml.
    <taskdef name="git-describe" classname="org.mdonoughe.JGitDescribeTask" classpath="jgit-describe.jar"/>
Use the new git-describe task to populate a property with a string describing the current HEAD revision. `dir` is the path to the .git directory. `property` is the name of the property to populate.
    <git-describe dir=".git" property="describe"/>
jgit-describe.jar includes its own copy of the necessary parts of jgit.
