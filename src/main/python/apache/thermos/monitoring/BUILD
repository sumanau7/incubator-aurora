#
# Copyright 2013 Apache Software Foundation
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

import os

python_library(
  name = 'detector',
  sources = ['detector.py'],
  dependencies = [
    pants('src/main/python/apache/thermos/common:path')
  ]
)

python_library(
  name = 'garbage',
  sources = ['garbage.py'],
  dependencies = [
    pants(':detector'),
    pants('3rdparty/python:twitter.common.dirutil'),
    pants('3rdparty/python:twitter.common.lang'),
    pants('3rdparty/python:twitter.common.quantity'),
    pants('src/main/python/apache/thermos/common:ckpt'),
    pants('src/main/python/apache/thermos/common:path'),
  ]
)

python_library(
  name = 'monitor',
  sources = ['monitor.py'],
  dependencies = [
    pants('3rdparty/python:twitter.common.log'),
    pants('3rdparty/python:twitter.common.recordio'),
    pants('src/main/python/apache/thermos/common:ckpt'),
    pants('src/main/thrift/org/apache/thermos:py-thrift'),
  ]
)

python_library(
  name = 'disk',
  sources = ['disk.py'],
  dependencies = [
    pants('3rdparty/python:watchdog'),
    pants('3rdparty/python:twitter.common.dirutil'),
    pants('3rdparty/python:twitter.common.exceptions'),
    pants('3rdparty/python:twitter.common.lang'),
    pants('3rdparty/python:twitter.common.log'),
    pants('3rdparty/python:twitter.common.quantity'),
  ]
)

python_library(
  name = 'process',
  sources = ['process.py'],
)

python_library(
  name = 'process_collector_psutil',
  sources = ['process_collector_psutil.py'],
  dependencies = [
    pants(':process'),
    pants('3rdparty/python:psutil'),
    pants('3rdparty/python:twitter.common.log'),
  ]
)

python_library(
  name = 'resource',
  sources = ['resource.py'],
  dependencies = [
    pants(':disk'),
    pants(':monitor'),
    pants(':process'),
    pants(':process_collector_psutil'),
    pants('3rdparty/python:twitter.common.collections'),
    pants('3rdparty/python:twitter.common.concurrent'),
    pants('3rdparty/python:twitter.common.lang'),
    pants('3rdparty/python:twitter.common.log'),
    pants('3rdparty/python:twitter.common.quantity'),
  ]
)

python_library(
  name = 'monitoring',
  dependencies = [
    pants(':detector'),
    pants(':disk'),
    pants(':garbage'),
    pants(':monitor'),
    pants(':process'),
    pants(':resource'),

    # covering dependency for common
    pants('src/main/python/apache/thermos/common'),
  ],
  provides = setup_py(
    name = 'apache.thermos.monitoring',
    version = open(os.path.join(get_buildroot(), '.auroraversion')).read().strip().upper(),
    description = 'Thermos monitoring library.',
  )
)
