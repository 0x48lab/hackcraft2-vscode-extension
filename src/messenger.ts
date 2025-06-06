import * as vscode from 'vscode'

export const handleMessages = (webview: vscode.Webview) => {
  receiveMessages(webview)

  sendMessages(webview)
}

const receiveMessages = (webview: vscode.Webview) => {
  webview.onDidReceiveMessage(async (message) => {
    let openPath: vscode.Uri

    switch (message.command) {
      case 'consoleLog':
        consoleLog(webview, message)
        return
      case 'loadConfig':
        loadConfig(webview, message)
        return
      case 'saveConfig':
        saveConfig(webview, message)
        return
      case 'getCurrentDocument':
        getCurrentDocument(webview, message)
        return
      case 'openFileExample':
        openPath = vscode.Uri.file(message.data)

        vscode.workspace.openTextDocument(openPath).then(async (doc) => {
          vscode.window.showTextDocument(doc)
        })
        return
    }
  })
}

const sendMessages = (webview: vscode.Webview) => {
  vscode.window.onDidChangeActiveTextEditor(async (editor) => {
    if (!editor) return

    const currentFile = editor.document.fileName

    await webview.postMessage({
      command: 'setCurrentFileExample',
      data: currentFile,
    })
  })
}

// -----------------------------------
const levelOrder = ['error', 'warn', 'info', 'debug'];
const shouldLog = (targetLevel: string): boolean => {
  const config = vscode.workspace.getConfiguration('hackCraft2')
  const settingsLogLevel = config.get('logLevel', 'info')

  return levelOrder.indexOf(targetLevel) <= levelOrder.indexOf(settingsLogLevel);
}

const consoleLog = async (webview: vscode.Webview, message: any) => {
  console.log('consoleLog', message)
  //Create output channel
  const log: vscode.LogOutputChannel = vscode.window.createOutputChannel("hackCraft2", { log: true });
  log.show(true);

  if (!shouldLog(message.level)) return

  switch (message.level) {
    case 'debug':
      log.info('[debug] --- start ---')
      log.info(message.data)
      log.info('[debug] --- end ---')
      break
    case 'warn':
      log.warn(message.data)
      break
    case 'error':
      log.error(message.data)
      break
    default:
      log.info(message.data)
      break
  }
}

const loadConfig = async (webview: vscode.Webview, message: any) => {
  console.log('loadConfig', message.command)
  // vscode.window.showInformationMessage('loadConfig');
  const config = vscode.workspace.getConfiguration('hackCraft2')

  let serverAddressHistory = []
  const serverAddressHistoryData: any = config.get('serverAddressHistory')
  if (serverAddressHistoryData) {
    serverAddressHistory = serverAddressHistoryData.split(',')
    serverAddressHistory = serverAddressHistory.filter((item: any) => item !== '')
  }

  let playerIdHistory = []
  const playerIdHistoryData: any = config.get('playerIdHistory')
  if (playerIdHistoryData) {
    playerIdHistory = playerIdHistoryData.split(',')
    playerIdHistory = playerIdHistory.filter((item: any) => item !== '')
  }

  const settings = {
    serverAddress: config.get('serverAddress'),
    playerId: config.get('playerId'),
    serverAddressHistory: serverAddressHistory,
    playerIdHistory: playerIdHistory,
  }
  console.log('loadConfig settings', settings)

  await webview.postMessage({
    command: 'onLoadConfig',
    data: settings,
  })
}

const saveConfig = async (webview: vscode.Webview, message: any) => {
  console.log('saveConfig', message.command)
  // vscode.window.showInformationMessage('saveConfig');
  const config = vscode.workspace.getConfiguration('hackCraft2')
  const settings = message.data
  console.log('saveConfig settings=', settings)

  config.update('serverAddress', settings.serverAddress, vscode.ConfigurationTarget.Workspace, true)
  config.update('playerId', settings.playerId, vscode.ConfigurationTarget.Workspace, true)

  let serverAddressHistory: any = config.get('serverAddressHistory', '')
  serverAddressHistory = settings.serverAddress + ',' + serverAddressHistory.replace(settings.serverAddress + ',', '')
  config.update('serverAddressHistory', serverAddressHistory, vscode.ConfigurationTarget.Workspace, true)

  let playerIdHistory: any = config.get('playerIdHistory', '')
  playerIdHistory = settings.playerId + ',' + playerIdHistory.replace(settings.playerId + ',', '')
  config.update('playerIdHistory', playerIdHistory, vscode.ConfigurationTarget.Workspace, true)

  await webview.postMessage({
    command: 'onSaveConfig',
    data: { message: 'saved' },
  })
}

const getCurrentDocument = async (webview: vscode.Webview, message: any) => {
  const editor = vscode.window.activeTextEditor
  const document = editor?.document

  const fileName = document?.fileName.replace(vscode.workspace.workspaceFolders?.[0].uri.path + '', '')
  console.log(fileName)

  const data = {
    fileName: fileName,
    languageId: document?.languageId,
    code: document?.getText()
  }

  await webview.postMessage({
    command: 'onGetCurrentDocument',
    data: data,
  })
}