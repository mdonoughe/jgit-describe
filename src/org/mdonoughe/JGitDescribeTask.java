package org.mdonoughe;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;

import org.eclipse.jgit.lib.Commit;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.Treeish;

public class JGitDescribeTask extends Task {
  private File dir;
  private int shalength;
  private String target;
  private String property;

  public void setDir(String path) {
    dir = new File(path);
  }

  public void setShalength(int length) {
    shalength = length;
  }

  public void setTarget(String description) {
    target = description;
  }

  public void setProperty(String oproperty) {
    property = oproperty;
  }

  public JGitDescribeTask() {
    dir = new File(".git");
    shalength = 7;
    target = "HEAD";
  }

  private static Map<ObjectId, String> collectTags(Repository r) {
    Map<ObjectId, String> map = new HashMap<ObjectId, String>();
    Map<String, Ref> refs = r.getTags();

    for(Map.Entry<String, Ref> tag : refs.entrySet()) {
      ObjectId tagcommit = tag.getValue().getObjectId();
      map.put(tagcommit, tag.getKey());
    }

    return map;
  }

  private static List<Commit> candidateCommits(Commit child, Map<ObjectId, String> tagmap) {
    Repository r = child.getRepository();
    Queue<Commit> q = new LinkedList<Commit>();
    q.add(child);
    List<Commit> taggedcommits = new LinkedList<Commit>();
    Set<ObjectId> seen = new HashSet<ObjectId>();

    while(q.size() > 0) {
      Commit commit = q.remove();
      if(tagmap.containsKey(commit.getCommitId())) {
        taggedcommits.add(commit);
        // don't consider commits that are farther away than this tag
        continue;
      }
      for(ObjectId oid : commit.getParentIds()) {
        if(!seen.contains(oid)) {
          seen.add(oid);
          try {
            q.add(r.mapCommit(oid));
          } catch(IOException e) {
            e.printStackTrace();
          }
        }
      }
    }

    return taggedcommits;
  }

  private static void seeAllParents(Commit child, Set<ObjectId> seen) {
    Repository r = child.getRepository();
    Queue<Commit> q = new LinkedList<Commit>();
    q.add(child);

    while(q.size() > 0) {
      Commit commit = q.remove();
      for(ObjectId oid : commit.getParentIds()) {
        seen.add(oid);
        try {
          q.add(r.mapCommit(oid));
        } catch(IOException e) {
          e.printStackTrace();
        }
      }
    }
  }

  private static int distanceBetween(Commit child, Commit parent) {
    Repository r = child.getRepository();
    Set<ObjectId> seena = new HashSet<ObjectId>();
    Set<ObjectId> seenb = new HashSet<ObjectId>();
    Queue<Commit> q = new LinkedList<Commit>();
    q.add(child);
    int distance = 0;
    ObjectId parentId = parent.getCommitId();

    while(q.size() > 0) {
      Commit commit = q.remove();
      ObjectId commitId = commit.getCommitId();

      if(seena.contains(commitId))
        continue;
      seena.add(commitId);

      if(parentId.equals(commitId)) {
        // don't consider commits that are included in this commit
        seeAllParents(commit, seenb);
        // remove things we shouldn't have included
        for(ObjectId oid : seenb) {
          if(seena.contains(oid)) {
            distance--;
          }
        }
        seena.addAll(seenb);
        continue;
      }

      for(ObjectId oid : commit.getParentIds()) {
        if(!seena.contains(oid)) {
          try {
            q.add(r.mapCommit(oid));
          } catch(IOException e) {
            e.printStackTrace();
          }
        }
      }
      distance++;
    }

    return distance;
  }

  public void execute() throws BuildException {
    if(property == null) {
      log("\"property\" attribute must be set!", Project.MSG_ERR);
      return;
    }
    
    if(!dir.exists()) {
      log("directory " + dir + " does not exist", Project.MSG_ERR);
      return;
    }

    Repository r = null;
    try {
      r = new Repository(dir);
    } catch(IOException e) {
      throw new BuildException("Could not open repository", e);
    }

    Commit c = null;
    try {
      c = r.mapCommit(target);
    } catch(IOException e) {
      throw new BuildException("Could not map commit " + target, e);
    }

    if(c == null) {
      throw new BuildException("Repository has no HEAD revision");
    }

    Map<ObjectId, String> tagmap = collectTags(r);

    List<Commit> taggedparentcommits = candidateCommits(c, tagmap);

    Commit best = null;
    int distance = 0;

    for(Commit commit : taggedparentcommits) {
      int thisdistance = distanceBetween(c, commit);
      if(best == null || thisdistance < distance) {
        best = commit;
        distance = thisdistance;
      }
    }

    StringBuilder sb = new StringBuilder();
    if(best != null) {
      sb.append(tagmap.get(best.getCommitId()));
      sb.append("-");
      sb.append(distance);
      sb.append("-g");
    }
    sb.append(c.getCommitId().abbreviate(r, shalength).name());
    getProject().setProperty(property, sb.toString());
  }
}
