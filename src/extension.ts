import * as vscode from 'vscode'
import { BaseViewProvider } from './BaseViewProvider'
import WebSocket from 'ws'

interface Location {
	x: number;
	y: number;
	z: number;
}

interface Block {
	name: string;
	data: number;
	isLiquid: boolean;
	isAir: boolean;
	isSolid: boolean;
	isPassable: boolean;
	x: number;
	y: number;
	z: number;
}

interface Entity {
	ownerUuid: string;
	entityUuid: string;
	name: string;
	type: string;
	isCreativeMode: boolean;
	movementSpeed: number;
	maxHealth: number;
	x: number;
	y: number;
	z: number;
	world: string;
}

interface ItemStack {
	slot: number;
	name: string;
	amount: number;
}

interface LoggedData {
	playerUUID: string;
	world: string;
	player_name: string;
	isOp: boolean;
	host: string;
	port: number;
	ssl: boolean;
	monacoPort: number;
	scratchPort: number;
	entities: Entity[];
	level: number;
}

let statusBarItem: vscode.StatusBarItem;
let ws: WebSocket | null = null;
let isConnected = false;
let entities: Entity[] = [];
let selectedEntity: Entity | null = null;
let logChannel: vscode.LogOutputChannel;
let isRunning = false;

function initializeLogChannel() {
	if (!logChannel) {
		logChannel = vscode.window.createOutputChannel("hackCraft2", { log: true });
		// Show the output channel immediately
		logChannel.show(true);
		logMessage('info', 'hackCraft2 extension activated');
	}
}

function logMessage(level: 'info' | 'warn' | 'error', message: string) {
	if (!logChannel) {
		initializeLogChannel();
	}
	logChannel[level](message);
}

function updateStatusBarItems() {
	if (!statusBarItem) {
		statusBarItem = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Right, 100);
		statusBarItem.command = 'hackcraft2.showConnectionMenu';
	}

	if (isConnected) {
		if (selectedEntity) {
			// 一時的に直接日本語を使用
			statusBarItem.text = `$(plug) hackCraft2: ${selectedEntity.name} (${selectedEntity.type})`;
			statusBarItem.tooltip = 'クリックして接続メニューを表示';
		} else {
			statusBarItem.text = `$(plug) hackCraft2: 接続中`;
			statusBarItem.tooltip = 'クリックして接続メニューを表示';
		}
	} else {
		statusBarItem.text = `$(plug) hackCraft2: 未接続`;
		statusBarItem.tooltip = 'クリックしてhackCraft2サーバーに接続';
	}
	statusBarItem.show();
}

async function showConnectionMenu() {
	if (!isConnected) {
		logMessage('info', 'サーバーに接続中...');
		await connect();
		return;
	}

	const items: vscode.QuickPickItem[] = [
		{
			label: `$(person) エンティティを選択`,
			description: '制御するエンティティを選択',
			detail: selectedEntity 
				? `現在: ${selectedEntity.name} (${selectedEntity.type})`
				: 'エンティティが選択されていません'
		},
		{
			label: `$(plug) 切断`,
			description: 'サーバーから切断',
			detail: '現在の接続を閉じる'
		}
	];

	const selected = await vscode.window.showQuickPick(items, {
		placeHolder: '接続メニュー',
	});

	if (!selected) {
		return;
	}

	if (selected.label.includes('エンティティを選択')) {
		await selectEntity();
	} else if (selected.label.includes('切断')) {
		await disconnect();
	}
}

async function selectEntity() {
	if (!isConnected) {
		logMessage('error', 'サーバーに接続されていません');
		vscode.window.showErrorMessage('サーバーに接続されていません');
		return;
	}

	if (entities.length === 0) {
		logMessage('warn', '利用可能なエンティティがありません');
		vscode.window.showInformationMessage('利用可能なエンティティがありません');
		return;
	}

	logMessage('info', `利用可能なエンティティ: ${entities.length}`);
	const items = entities.map(entity => ({
		label: entity.name,
		description: `タイプ: ${entity.type}`,
		detail: [
			`位置: (${entity.x}, ${entity.y}, ${entity.z})`,
			`体力: ${entity.maxHealth}`,
			`クリエイティブ: ${entity.isCreativeMode}`
		].join(' | '),
		entity: entity
	}));

	const selected = await vscode.window.showQuickPick(items, {
		placeHolder: '制御するエンティティを選択',
		matchOnDescription: true,
		matchOnDetail: true
	});

	if (selected) {
		selectedEntity = selected.entity;
		logMessage('info', `選択されたエンティティ: ${selectedEntity.name} (${selectedEntity.type})`);
		updateStatusBarItems();
		
		// Send attach message to server
		if (ws) {
			const message = {
				type: 'attach',
				data: {
					entityUuid: selectedEntity.entityUuid,
				},
			};
			logMessage('info', `エンティティ ${selectedEntity.name} にアタッチメッセージを送信`);
			ws.send(JSON.stringify(message));
		}
	}
}

async function connect() {
	if (ws) {
		ws.close();
		ws = null;
	}

	const config = vscode.workspace.getConfiguration('8x9craft2');
	const serverAddress = config.get('serverAddress', 'localhost:25570');
	const playerId = config.get('playerId', '');

	logMessage('info', `Connecting to server: ${serverAddress}`);

	if (!playerId) {
		logMessage('error', 'Player ID not set in settings');
		vscode.window.showErrorMessage('Please set your player ID in settings');
		return;
	}

	try {
		ws = new WebSocket(`ws://${serverAddress}/ws`);
		
		ws.on('open', () => {
			isConnected = true;
			updateConnectionState(true);
			entities = [];
			selectedEntity = null;
			updateStatusBarItems();
			logMessage('info', 'Connected to server');
			vscode.window.showInformationMessage('Connected to 8x9craft2 server');
			
			// Send login message
			const message = {
				type: 'login',
				data: {
					player: playerId,
					clientType: 'main',
				},
			};
			logMessage('info', `Sending login message for player: ${playerId}`);
			ws?.send(JSON.stringify(message));
		});

		ws.on('close', () => {
			isConnected = false;
			updateConnectionState(false);
			isRunning = false;
			updateRunningState(false);
			entities = [];
			selectedEntity = null;
			updateStatusBarItems();
			logMessage('info', 'Disconnected from server');
			vscode.window.showInformationMessage('Disconnected from 8x9craft2 server');
		});

		ws.on('error', (error) => {
			isConnected = false;
			updateConnectionState(false);
			isRunning = false;
			updateRunningState(false);
			entities = [];
			selectedEntity = null;
			updateStatusBarItems();
			logMessage('error', `Connection error: ${error.message}`);
			vscode.window.showErrorMessage(`Connection error: ${error.message}`);
		});

		ws.on('message', (data) => {
			const json = JSON.parse(data.toString());
			logMessage('info', `Received message: ${data.toString()}`);
			
			if (json.type === 'logged') {
				const loggedData = json.data as LoggedData;
				logMessage('info', 'Login successful');
				logMessage('info', `Player: ${loggedData.player_name}`);
				logMessage('info', `World: ${loggedData.world}`);
				logMessage('info', `Is OP: ${loggedData.isOp}`);
				
				// Update entities from logged data
				entities = loggedData.entities;
				logMessage('info', `Received ${entities.length} entities`);
				
				if (entities.length > 0 && !selectedEntity) {
					// Auto-select first entity if none selected
					selectedEntity = entities[0];
					logMessage('info', `Auto-selected entity: ${selectedEntity.name} (${selectedEntity.type})`);
					const message = {
						type: 'attach',
						data: {
							entityUuid: selectedEntity.entityUuid,
						},
					};
					logMessage('info', `Sending attach message for auto-selected entity: ${selectedEntity.name}`);
					ws?.send(JSON.stringify(message));
				}
				updateStatusBarItems();
			} else if (json.type === 'error') {
				logMessage('error', `Server error: ${json.data}`);
			}

			// 実行状態の更新を処理
			if (json.type === 'execute_start') {
				isRunning = true;
				updateRunningState(true);
			} else if (json.type === 'execute_end' || json.type === 'execute_error') {
				isRunning = false;
				updateRunningState(false);
			}
		});

	} catch (error) {
		logMessage('error', `Failed to connect: ${error}`);
		vscode.window.showErrorMessage(`Failed to connect: ${error}`);
		isConnected = false;
		updateConnectionState(false);
		isRunning = false;
		updateRunningState(false);
		entities = [];
		selectedEntity = null;
		updateStatusBarItems();
	}
}

async function disconnect() {
	if (ws) {
		ws.close();
		ws = null;
	}
	isConnected = false;
	updateConnectionState(false);
	entities = [];
	selectedEntity = null;
	updateStatusBarItems();
}

function createCompletionItem(name: string, args: string, desc: string, returnType: string): vscode.CompletionItem {
	const item = new vscode.CompletionItem(name, vscode.CompletionItemKind.Function);
	item.insertText = new vscode.SnippetString(args);
	item.documentation = new vscode.MarkdownString(desc);
	item.detail = returnType;
	return item
}

async function runFile(language: 'python' | 'javascript' | 'java') {
	if (!isConnected || !selectedEntity) {
		logMessage('error', 'Not connected or no entity selected');
		vscode.window.showErrorMessage('Please connect and select an entity first');
		return;
	}

	const editor = vscode.window.activeTextEditor;
	if (!editor) {
		logMessage('error', 'No active editor');
		vscode.window.showErrorMessage('No active editor');
		return;
	}

	const document = editor.document;
	const fileLanguage = getLanguageFromFile(document.fileName);
	if (!fileLanguage || fileLanguage !== language) {
		logMessage('error', `File type does not match ${language}`);
		vscode.window.showErrorMessage(`Please use a ${language} file`);
		return;
	}

	const code = document.getText();
	if (ws) {
		const message = {
			type: 'run',
			data: {
				language: language,
				name: document.fileName,
				entity: selectedEntity.entityUuid,
				code: code,
			},
		};
		logMessage('info', `Sending execute message for ${language}`);
		ws.send(JSON.stringify(message));
	}
}

// 実行状態を更新する関数
function updateRunningState(running: boolean) {
	isRunning = running;
	// コマンドの有効/無効状態を更新
	vscode.commands.executeCommand('setContext', 'hackcraft2.isRunning', running);
}

// 接続状態を更新する関数
function updateConnectionState(connected: boolean) {
	isConnected = connected;
	// コマンドの有効/無効状態を更新
	vscode.commands.executeCommand('setContext', 'hackcraft2.isConnected', connected);
}

// スクリプト停止コマンドの実装
async function stopScript() {
	if (!isConnected || !selectedEntity) {
		logMessage('error', 'Not connected or no entity selected');
		vscode.window.showErrorMessage('Please connect and select an entity first');
		return;
	}

	if (ws) {
		const message = {
			type: 'stop',
			data: {
				entityUuid: selectedEntity.entityUuid,
			},
		};
		logMessage('info', 'Sending stop message');
		ws.send(JSON.stringify(message));
	}
}

export function activate(context: vscode.ExtensionContext) {
	// Initialize log channel first
	initializeLogChannel();

	// Create status bar items
	updateStatusBarItems();

	// Register commands
	let showConnectionMenuCommand = vscode.commands.registerCommand('hackcraft2.showConnectionMenu', showConnectionMenu);
	let getSelectedEntityUuidCommand = vscode.commands.registerCommand('hackcraft2.getSelectedEntityUuid', () => {
		if (!selectedEntity) {
			throw new Error('No entity selected');
		}
		return selectedEntity.entityUuid;
	});

	//auto complete 
	const provider1 = vscode.languages.registerCompletionItemProvider('javascript', {

		provideCompletionItems(document: vscode.TextDocument, position: vscode.Position, token: vscode.CancellationToken, context: vscode.CompletionContext) {
			const entityCharacterCompletion = new vscode.CompletionItem('entity');
			entityCharacterCompletion.commitCharacters = ['.'];
			entityCharacterCompletion.documentation = new vscode.MarkdownString('Press `.` to get `entity.`');

			const timeCharacterCompletion = new vscode.CompletionItem('time');
			timeCharacterCompletion.commitCharacters = ['.'];
			timeCharacterCompletion.documentation = new vscode.MarkdownString('Press `.` to get `time.`');

			const consoleCharacterCompletion = new vscode.CompletionItem('console');
			consoleCharacterCompletion.commitCharacters = ['.'];
			consoleCharacterCompletion.documentation = new vscode.MarkdownString('Press `.` to get `console.`');
			
			return [
				entityCharacterCompletion,timeCharacterCompletion,consoleCharacterCompletion
			];
		}
	});

	//todo このデータはTypeScriptの型定義ファイルから取得するようにしたい
	const provider2 = vscode.languages.registerCompletionItemProvider(
		'javascript',
		{
			provideCompletionItems(document: vscode.TextDocument, position: vscode.Position) {

				const linePrefix = document.lineAt(position).text.substr(0, position.character);
				if (linePrefix.endsWith('entity.')) {
					return [
						createCompletionItem('isAI', 'isAI()', 'エンティティーのAIが有効になっているか返す', 'boolean'),
						createCompletionItem('setAI', 'setAI(${1:arg1})', 'AIを有効にする', 'void'),
						createCompletionItem('setup', 'setup()', 'エンティティーの位置をブロックにスナップし北を向いた状態で停止状態にする', 'void'),
						createCompletionItem('teardown', 'teardown()', 'エンティティーを停止状態から解除します', 'void'),
						createCompletionItem('getHeight', 'getHeight()', 'エンティティーの高さを返す', 'number'),
						createCompletionItem('getWidth', 'getWidth()', 'エンティティーの幅を返す', 'number'),
						createCompletionItem('getHealth', 'getHealth()', 'エンティティーの体力を返す','number'),
						createCompletionItem('getName', 'getName()', 'エンティティーの名前を返す', 'string'),					
						createCompletionItem('getType', 'getType()', 'エンティティーの種類を返す', 'string'),					
						createCompletionItem('uniqueId', 'uniqueId()', 'エンティティーのUUID', 'string'),
						createCompletionItem('getPosition', 'getPosition()', 'エンティティの位置を返す', 'Location'),
						createCompletionItem('getDirection', 'getDirection()', 'エンティティーの向きを返す', 'Location'),
						createCompletionItem('distance', 'distance(${1:x}, ${2:y}, ${3:z})', '指定された座標までの距離を返す', 'number'),
						createCompletionItem('lookAt', 'lookAt(${1:ｘ}, ${2:y}, ${3:z})', '指定された座標を向く', 'void'),
						createCompletionItem('lockOnTarget', 'lockOnTarget(${1:arg1})', 'ターゲットを攻撃する', 'void'),
						createCompletionItem('teleport', 'teleport(${1:arg1}, ${2:arg2}, ${3:arg3})', '指定した場所に移動', 'void'),
						createCompletionItem('move', 'move(${1:arg1})', '向いている方向に加速度を与える', 'boolean'),
						createCompletionItem('turn', 'turn(${1:arg1})', '指定した角度だけ向きを変える', 'void'),
						createCompletionItem('forward', 'forward()', '一歩前へ進む', 'boolean'),
						createCompletionItem('back', 'back()', '一歩後ろへ進む', 'boolean'),
						createCompletionItem('up', 'up()', '一歩上へ進む', 'boolean'),
						createCompletionItem('down', 'down()', '一歩下へ進む', 'boolean'),
						createCompletionItem('turnLeft', 'turnLeft()', '左を向く', 'void'),
						createCompletionItem('turnRight()', 'turnRight()', '右を向く', 'void'),
						createCompletionItem('stepLeft', 'stepLeft()', '一歩左へステップ', 'boolean'),
						createCompletionItem('stepRight', 'stepRight()', '一歩右へステップ', 'boolean'),
						createCompletionItem('jump', 'jump()', 'ジャンプする', 'boolean'),
						createCompletionItem('grabItem', 'grabItem(${1:slot})', '指定されたスロットのアイテムを持つ', 'boolean'),
						createCompletionItem('placeX', 'placeX(${1:height}, ${2:distance})', '持っているアイテムを指定された位置に置く', 'boolean'),
						createCompletionItem('place', 'place()', '持っているアイテムを前に置く', 'boolean'),
						createCompletionItem('placeDown', 'placeDown()', '持っているアイテムを下に置く', 'boolean'),
						createCompletionItem('placeUp', 'placeUp()', '持っているアイテムを上に置く', 'boolean'),
						createCompletionItem('actionX', 'actionX(${1:height}, ${2:distance})', '右クリックアクションを指定された位置にする', 'boolean'),
						createCompletionItem('action', 'action()', '右クリックアクションを前にする', 'boolean'),
						createCompletionItem('actionUp', 'actionUp()', '右クリックアクションを上にする', 'boolean'),
						createCompletionItem('actionDown', 'actionDown()', '右クリックアクションを下にする', 'boolean'),
						createCompletionItem('useItemX', 'useItem(${1:height}, ${2:distance})', '持っているアイテムを指定された位置に使う', 'boolean'),
						createCompletionItem('useItem', 'useItem()', '指持っているアイテムを前に使う', 'boolean'),
						createCompletionItem('useItemUp', 'useItemUp()', '指持っているアイテムを上に使う', 'boolean'),
						createCompletionItem('useItemDown', 'useItemDown()', '指持っているアイテムを下に使う', 'boolean'),
						createCompletionItem('digX', 'digX(${1:height}, ${2:distance})', '指定された位置を掘る', 'boolean'),
						createCompletionItem('dig', 'dig()', '前を掘る', 'boolean'),
						createCompletionItem('digDown', 'digDown()', '下を掘る', 'boolean'),
						createCompletionItem('digUp', 'digUp()', '上を掘る', 'boolean'),
						createCompletionItem('isBlockedX', 'isBlockedX(${1:height}, ${2:distance})', '指定された位置にブロックがあるかどうかを返す', 'boolean'),
						createCompletionItem('isBlocked', 'isBlocked()', '前にブロックがあるかどうかを返す', 'boolean'),
						createCompletionItem('isBlockedUp', 'isBlockedUp()', '上にブロックがあるかどうかを返す', 'boolean'),
						createCompletionItem('isBlockedDown', 'isBlockedDown()', '下にブロックがあるかどうかを返す', 'boolean'),
						createCompletionItem('meleeAttack', 'meleeAttack(${1:degrees})', '指定された向きを直接攻撃', 'boolean'),
						createCompletionItem('rangedAttack', 'rangedAttack(${1:x}, ${2:y}, ${3:z})', '指定された位置へ遠隔攻撃', 'boolean'),
						createCompletionItem('inspect', 'inspect(${1:height}, ${2:horizontal}, ${3:distance})', '自分の足元を中心に指定された位置のブロックの情報を返す', 'Block'),
						createCompletionItem('scan', 'scan(${1:height}, ${2:horizontal}, ${3:distance})', '自分の足元を中心に指定された範囲のエンティティ情報を返す', 'Entity[]'),					
						createCompletionItem('stop', 'stop()', '動かないようにする', 'void'),					
						createCompletionItem('getItem', 'getItem(${1:slot})', '指定されたスロットのアイテム情報を返す', 'ItemStack'),
						createCompletionItem('setItem', 'getItem(${1:slot}, ${2:item})', '指定されたスロットにアイテムを設定する', 'boolean'),
						createCompletionItem('swapItem', 'swapItem(${1:slot1}, ${2:slot2})', '指定されたスロットのアイテムを交換する', 'boolean'),
						createCompletionItem('dropItem', 'dropItem(${1:slot})', '指定されたスロットのアイテムをドロップする', 'boolean'),					
						createCompletionItem('pickupItems', 'pickupItems()', '周辺のアイテムを拾う', 'number'),					
						createCompletionItem('storeInChest', 'storeInChest(${1:height}, ${2:distance})', '指定された位置にあるインベントリにアイテムを格納する', 'boolean'),
					];
				}
				else if (linePrefix.endsWith('time.')) {
					return [
						createCompletionItem('sleep', 'sleep(${1:millisecond})', '指定されたミリ秒スリープする', 'void'),				
					];
				}
				else if (linePrefix.endsWith('console.')) {
					return [
						createCompletionItem('log', 'log(${1:message})', 'コンソールに出力', 'void'),				
					];
				}
				else {
					return undefined;
				}
			}
		},
		'.' // triggered whenever a '.' is being typed
	);
	const provider = new BaseViewProvider(context.extensionUri)

	context.subscriptions.push(
		provider1,
		provider2,
		showConnectionMenuCommand,
		getSelectedEntityUuidCommand,
		statusBarItem,
		vscode.window.registerWebviewViewProvider(
			BaseViewProvider.viewType,
			provider
		)
	);

	// Register run commands
	let runPythonCommand = vscode.commands.registerCommand('hackcraft2.runPython', () => runFile('python'));
	let runJavaScriptCommand = vscode.commands.registerCommand('hackcraft2.runJavaScript', () => runFile('javascript'));
	let runJavaCommand = vscode.commands.registerCommand('hackcraft2.runJava', () => runFile('java'));

	context.subscriptions.push(
		runPythonCommand,
		runJavaScriptCommand,
		runJavaCommand
	);

	// 初期状態の設定
	updateConnectionState(false);
	updateRunningState(false);

	// 停止コマンドの登録
	let stopScriptCommand = vscode.commands.registerCommand('hackcraft2.stopScript', stopScript);

	context.subscriptions.push(
		stopScriptCommand
	);
}

export function deactivate() {
	if (ws) {
		ws.close();
	}
	if (statusBarItem) {
		statusBarItem.dispose();
	}
	if (logChannel) {
		logChannel.dispose();
	}
}

function getLanguageFromFile(fileName: string): 'python' | 'javascript' | 'java' | null {
	const extension = fileName.split('.').pop()?.toLowerCase();
	switch (extension) {
		case 'py':
			return 'python';
		case 'js':
			return 'javascript';
		case 'java':
			return 'java';
		default:
			return null;
	}
}
