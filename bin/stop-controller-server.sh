#!/usr/bin/env bash

# Copyright (C) 2011 Near Infinity Corporation
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

bin=`dirname "$0"`
bin=`cd "$bin"; pwd`

. "$bin"/blur-config.sh

PID_FILE=$BLUR_HOME/pids/controller.pid

if [ -f $PID_FILE ]; then
  if kill -0 `cat $PID_FILE` > /dev/null 2>&1; then
    echo Stopping Controller server with pid [`cat $PID_FILE`].
    kill `cat $PID_FILE`
  else
    echo No Controller server to stop
  fi
else
  echo No Controller server to stop
fi