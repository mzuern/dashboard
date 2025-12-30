@echo off
set "NODE_HOME=C:\Users\%USERNAME%\tools\node\node-v20.11.1-win-x64"
set "PATH=%PATH%;%NODE_HOME%"
echo Using NODE_HOME=%NODE_HOME%
node -v
npm -v