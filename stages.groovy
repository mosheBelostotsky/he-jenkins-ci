/*
 * Copyright 2016 Hewlett-Packard Development Company, L.P.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * Software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

import groovy.json.*

def utils

def init(){
  // load utils lib
  utils = fileLoader.fromGit('utils',
    'https://github.com/eedevops/he-jenkins-ci.git', 'master',
    null, '')
}

def cleanup_workspace(){
  // cleanup workspace
  stage 'Clean workspace'
  deleteDir()
}

def co_source(){
  // check out source
  stage 'Checkout source'
  checkout scm
  // get version from package.json
  def version = utils.get_version ('package.json')
  echo "version ${version}"
  def changeUrl =
    utils.check_pr(env) ? "\nChange URL: ${env.CHANGE_URL}" : "";
  return ["changeUrl": changeUrl, "version": version]
}

def build(){
  stage 'build'
  sh 'npm install'
}

def lint(){
  stage 'linter'
  sh 'npm run coffeelint'
  sh 'npm run jslint'
  step([$class: 'WarningsPublisher', canComputeNew: false,
    canResolveRelativePaths: false, defaultEncoding: '', excludePattern: '',
    healthy: '', includePattern: '', messagesPattern: '',
    parserConfigurations: [[parserName: 'JSLint', pattern: '*lint.xml']],
    unHealthy: ''])
}

def test(){
  stage 'test'
  sh 'npm test || true'
  step([$class: 'JUnitResultArchiver', testResults: 'test/xunit.xml'])
}

def package_and_release(version){
  stage 'package'
  def changes = utils.get_tags_diff()
  sh "git tag v${version}" // -F ${changes}"
  sshagent(['github-ssh']) { sh "git -c core.askpass=true push --tags" }
  def gconf = utils.guess_github_settings()
  utils.create_version_json (gconf.api, gconf.project, gconf.cred,
    "${version}", "v${version}", true, readFile(changes))
  echo 'done!'
  return readFile(changes)
}

return this;
