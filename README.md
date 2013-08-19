SPiD SDK for Java
=================

Supported java versions are 1.6 or greater.

## Aftonbladet fork information
Aftonbladet maintains a fork from spids repo. 
Aftonbladets master branch is meant to be kept in sync with spids master
The branch 'ab-sdk-java' is made to keep aftonbladets unique changes. This is required to be able to perform releases for nexus/maven, since spid does not deploy artifacts in any public maven repo.  

### Workflow to make changes

1. Work in master: `git checkout master`
1. Ensure master is in sync with spid, see https://help.github.com/articles/syncing-a-fork
2. Make changes in master and test locally
3. Pull the changes in to aftonbladets branch: `git checkout ab-sdk-java`
4. Commit & Push -> Changes will be built in Bamboo. If satisfied make a release by manually triggering the "Build release" phase. 
5. Happily use the now published artifact from ab-nexus.
6. If changes should be contributed back to spid: Make a Github pull request from master.
