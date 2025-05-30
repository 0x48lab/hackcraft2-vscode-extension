import * as vscode from 'vscode'
import WebSocket from 'ws'
import { KotlinParser, ExportedFunction } from './KotlinParser'
import * as path from 'path'
import * as fs from 'fs'

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
	isRunning: boolean;
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

// グローバル変数として関数を保持
let updateConnectionStateOriginal: (connected: boolean) => void;
let selectEntityOriginal: () => Promise<void>;

// イベントエミッターを追加
const onConnectionStateChanged = new vscode.EventEmitter<boolean>();
const onEntitySelected = new vscode.EventEmitter<void>();

// グローバル変数としてステータスバーアイテムを保持
let open3DViewButton: vscode.StatusBarItem;

// loggedイベントで受け取ったデータを保持する変数を追加
let loggedData: {
	playerUUID: string;
	world: string;
	player_name: string;
	isOp: boolean;
	host: string;
	port: number;
	ssl: boolean;
	monacoPort: number;
	scratchPort: number;
	level: number;
} | null = null;

function initializeLogChannel() {
	if (!logChannel) {
		logChannel = vscode.window.createOutputChannel("hackCraft2", { log: true });
		// Show the output channel immediately
		logChannel.show(true);
		logMessage('info', 'hackCraft2 extension activated');
	}
}

function logMessage(level: 'info' | 'warn' | 'error' | 'debug', message: string) {
	if (!logChannel) {
		initializeLogChannel();
	}
	logChannel[level](message);
}

function updateStatusBarItems() {
	// メインのステータスバーアイテムの更新
	if (!statusBarItem) {
		statusBarItem = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Right, 100);
		statusBarItem.command = 'hackcraft2.showConnectionMenu';
	}

	if (isConnected) {
		if (selectedEntity) {
			statusBarItem.text = `$(hackcraft2-icon) hackCraft2: ${selectedEntity.name} (${selectedEntity.type})`;
			statusBarItem.tooltip = 'クリックして接続メニューを表示';
		} else {
			statusBarItem.text = `$(hackcraft2-icon) hackCraft2: 接続中`;
			statusBarItem.tooltip = 'クリックして接続メニューを表示';
		}
	} else {
		statusBarItem.text = `$(hackcraft2-icon) hackCraft2: 未接続`;
		statusBarItem.tooltip = 'クリックしてhackCraft2サーバーに接続';
	}
	statusBarItem.show();

	// 3Dビューボタンの更新
	if (open3DViewButton) {
		if (isConnected && selectedEntity) {
			open3DViewButton.text = `$(eye) 3Dビューを開く`;
			open3DViewButton.tooltip = `${selectedEntity.name}の3Dビューを開く`;
			open3DViewButton.show();
		} else {
			open3DViewButton.hide();
		}
	}
}

async function showConnectionMenu() {
	if (!isConnected) {
		const items: vscode.QuickPickItem[] = [
			{
				label: `$(plug) 接続`,
				description: 'hackCraft2サーバーに接続',
				detail: 'サーバーアドレスとプレイヤーIDを使用して接続'
			},
			{
				label: `$(settings) 設定を開く`,
				description: 'hackCraft2の設定を開く',
				detail: 'サーバーアドレスやプレイヤーIDなどの設定を変更'
			}
		];

		const selected = await vscode.window.showQuickPick(items, {
			placeHolder: '接続メニュー',
		});

		if (!selected) {
			return;
		}

		if (selected.label.includes('接続')) {
			logMessage('info', 'サーバーに接続中...');
			await connect();
		} else if (selected.label.includes('設定を開く')) {
			// hackCraft2の設定画面を開く
			await vscode.commands.executeCommand('workbench.action.openSettings', '@ext:masafumiterazono.hackcraft2');
		}
		return;
	}

	// 接続済みの場合のメニュー
	const items: vscode.QuickPickItem[] = [
		{
			label: `$(person) エンティティを選択`,
			description: '制御するエンティティを選択',
			detail: selectedEntity 
				? `現在: ${selectedEntity.name} (${selectedEntity.type})`
				: 'エンティティが選択されていません'
		},
		{
			label: `$(settings) 設定を開く`,
			description: 'hackCraft2の設定を開く',
			detail: 'サーバーアドレスやプレイヤーIDなどの設定を変更'
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
	} else if (selected.label.includes('設定を開く')) {
		// hackCraft2の設定画面を開く
		await vscode.commands.executeCommand('workbench.action.openSettings', '@ext:masafumiterazono.hackcraft2');
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
		// エンティティ選択状態をVSCodeに通知
		vscode.commands.executeCommand('setContext', 'hackcraft2.selectedEntity', true);
		
		// Send attach message to server
		if (ws) {
			const message = {
				type: 'attach',
				data: {
					entityUuid: selectedEntity.entityUuid,
				},
			};
			ws.send(JSON.stringify(message));
		}

		// 選択したエンティティの実行状態を反映
		updateRunningState(selectedEntity.isRunning);

		updateStatusBarItems();
		onEntitySelected.fire();
	}
}

async function connect() {
	if (ws) {
		ws.close();
		ws = null;
	}

	const config = vscode.workspace.getConfiguration('hackCraft2');
	const serverHost = config.get('serverHost', 'localhost');
	const serverPort = config.get('serverPort', 25570);
	const playerId = config.get('playerId', '');

	const serverAddress = `${serverHost}:${serverPort}`;
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
			vscode.window.showInformationMessage('Connected to hackCraft2 server');
			
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
			updateRunningState(false);
			entities = [];
			selectedEntity = null;
			loggedData = null;  // loggedDataをクリア
			updateStatusBarItems();
			logMessage('info', 'Disconnected from server');
			vscode.window.showInformationMessage('Disconnected from hackCraft2 server');
		});

		ws.on('error', (error) => {
			isConnected = false;
			updateConnectionState(false);
			updateRunningState(false);
			entities = [];
			selectedEntity = null;
			loggedData = null;  // loggedDataをクリア
			updateStatusBarItems();
			logMessage('error', `Connection error: ${error.message}`);
			vscode.window.showErrorMessage(`Connection error: ${error.message}`);
		});

		ws.on('message', (data) => {
			const json = JSON.parse(data.toString());
			logMessage('debug', data.toString());
			
			if (json.type === 'logged') {
				const data = json.data as LoggedData;
				logMessage('info', 'Login successful');
				logMessage('info', `Player: ${data.player_name}`);
				logMessage('info', `World: ${data.world}`);
				logMessage('info', `Is OP: ${data.isOp}`);
				
				// loggedデータを保持
				loggedData = {
					playerUUID: data.playerUUID,
					world: data.world,
					player_name: data.player_name,
					isOp: data.isOp,
					host: data.host,
					port: data.port,
					ssl: data.ssl,
					monacoPort: data.monacoPort,
					scratchPort: data.scratchPort,
					level: data.level
				};
				
				// Update entities from logged data
				entities = data.entities;
				// 各エンティティの実行状態を初期化
				entities.forEach(entity => {
					entity.isRunning = false;
				});
				logMessage('debug', `Received ${entities.length} entities`);
				
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
					// 選択したエンティティの実行状態を反映
					updateRunningState(selectedEntity.isRunning);
				}
				updateStatusBarItems();
			} else if (json.type === 'error' || json.type === 'result') {
				logMessage('info', json.data);
				// エンティティの実行状態を更新
				if (selectedEntity && json.data.entityUuid === selectedEntity.entityUuid) {
					updateRunningState(false);
				}
				// 該当するエンティティの実行状態を更新
				const entity = entities.find(e => e.entityUuid === json.data.entityUuid);
				if (entity) {
					entity.isRunning = false;
				}
			} else if (json.type === 'message') {
				logMessage('info', json.data);
			} else if (json.type === 'attach') {
				//logMessage('debug', json.data);
			}
		});

	} catch (error) {
		logMessage('error', `Failed to connect: ${error}`);
		vscode.window.showErrorMessage(`Failed to connect: ${error}`);
		isConnected = false;
		updateConnectionState(false);
		updateRunningState(false);
		entities = [];
		selectedEntity = null;
		loggedData = null;  // loggedDataをクリア
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
	loggedData = null;  // loggedDataをクリア
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
		updateRunningState(true);
	}
}

// 実行状態を更新する関数
function updateRunningState(running: boolean) {
	isRunning = running;
	// 選択中のエンティティの実行状態も更新
	if (selectedEntity) {
		selectedEntity.isRunning = running;
	}
	// コマンドの有効/無効状態を更新
	vscode.commands.executeCommand('setContext', 'hackcraft2.isRunning', running);
	
	// ボタンの表示を更新
	const editor = vscode.window.activeTextEditor;
	if (editor) {
		const language = getLanguageFromFile(editor.document.fileName);
		if (language) {
			// 実行中は停止ボタンを表示し、実行ボタンを非表示に
			vscode.commands.executeCommand('setContext', `hackcraft2.canRun${language.charAt(0).toUpperCase() + language.slice(1)}`, !running);
			vscode.commands.executeCommand('setContext', 'hackcraft2.canStop', running);
		}
	}
}

// 接続状態を更新する関数を修正
function updateConnectionState(connected: boolean) {
	isConnected = connected;
	// コマンドの有効/無効状態を更新
	vscode.commands.executeCommand('setContext', 'hackcraft2.isConnected', connected);
	// 接続が切れた場合は選択されたエンティティもクリア
	if (!connected) {
		selectedEntity = null;
		vscode.commands.executeCommand('setContext', 'hackcraft2.selectedEntity', false);
	}
	onConnectionStateChanged.fire(connected);
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
				entity: selectedEntity.name,
			},
		};
		logMessage('info', 'Sending stop message');
		ws.send(JSON.stringify(message));
		updateRunningState(false);
	}
}

// トグルコマンドの実装
async function toggleScript() {
	if (isRunning) {
		await stopScript();
	} else {
		const editor = vscode.window.activeTextEditor;
		if (editor) {
			const language = getLanguageFromFile(editor.document.fileName);
			if (language) {
				await runFile(language);
			}
		}
	}
}

export function activate(context: vscode.ExtensionContext) {
	// Initialize log channel first
	initializeLogChannel();
	logMessage('info', 'Extension activated');

	// 3Dビューを開くボタンを追加
	open3DViewButton = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Right, 101);
	open3DViewButton.command = "hackcraft2.open3DView";
	open3DViewButton.text = "$(eye) 3Dビューを開く";
	open3DViewButton.tooltip = "選択したエンティティの3Dビューを開く";

	// イベントリスナーを登録
	context.subscriptions.push(
		onConnectionStateChanged.event(() => {
			updateStatusBarItems();
		}),
		onEntitySelected.event(() => {
			updateStatusBarItems();
		})
	);
	
	context.subscriptions.push(open3DViewButton);

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

	// Load Kotlin template file
	const templatePath = path.join(context.extensionPath, 'resources', 'templates', 'kotlin', 'exported_functions.kt');
	let exportedFunctions: ExportedFunction[] = [];

	try {
		const templateContent = fs.readFileSync(templatePath, 'utf8');
		exportedFunctions = KotlinParser.parseKotlinFile(templateContent);
	} catch (error) {
		logMessage('error', `Failed to load Kotlin template file: ${error}`);
	}

	// Register completion provider for all supported languages
	const completionProvider = vscode.languages.registerCompletionItemProvider(
		[
			{ language: 'javascript', scheme: 'file' },
			{ language: 'python', scheme: 'file' },
			{ language: 'java', scheme: 'file' }
		],
		{
			provideCompletionItems(document: vscode.TextDocument, position: vscode.Position) {
				const linePrefix = document.lineAt(position).text.substr(0, position.character);
				
				// First level completion (global objects)
				if (!linePrefix.includes('.')) {
					const items = [
						createGlobalObjectCompletion('entity', 'ペットを制御するためのオブジェクト'),
						createGlobalObjectCompletion('time', '時間を制御するためのオブジェクト'),
						createGlobalObjectCompletion('console', 'コンソール出力を制御するためのオブジェクト'),
						createGlobalObjectCompletion('system', 'システム機能を提供するオブジェクト'),
						createGlobalObjectCompletion('crab', 'ペットを制御するためのオブジェクト（8x9Craft互換）')
					];
					return new vscode.CompletionList(items, false);
				}

				// Second level completion (methods)
				const globalObjects = ['system', 'entity', 'crab', 'time', 'console'];
				const objectMatch = linePrefix.match(new RegExp(`(${globalObjects.join('|')})\\.`));
				if (!objectMatch) {
					return undefined;
				}

				const objectName = objectMatch[1];
				let items: vscode.CompletionItem[] = [];

				// Get functions from Kotlin template for system, entity, and crab
				if (['system', 'entity', 'crab'].includes(objectName)) {
					const objectFunctions = KotlinParser.getFunctionsByObject(exportedFunctions, objectName);
					items = KotlinParser.createCompletionItems(objectFunctions);
				}
				// Add built-in functions for time and console
				else if (objectName === 'time') {
					items = [
						createCompletionItem('sleep', 'sleep(${1:millisecond})', '指定されたミリ秒スリープする', 'void')
					];
				}
				else if (objectName === 'console') {
					items = [
						createCompletionItem('log', 'log(${1:message})', 'コンソールに出力', 'void')
					];
				}

				// Set priority for our completion items
				items.forEach(item => {
					item.sortText = '0' + item.label;
					item.preselect = true;
				});

				return new vscode.CompletionList(items, true);
			}
		},
		'.' // triggered whenever a '.' is being typed
	);

	// Helper function to create global object completion items
	function createGlobalObjectCompletion(name: string, description: string): vscode.CompletionItem {
		const item = new vscode.CompletionItem(name, vscode.CompletionItemKind.Variable);
		item.commitCharacters = ['.'];
		item.documentation = new vscode.MarkdownString(description);
		item.detail = 'Global Object';
		item.sortText = '0' + name;
		return item;
	}

	context.subscriptions.push(
		completionProvider,  // Replace provider1 and provider2 with the new unified provider
		showConnectionMenuCommand,
		getSelectedEntityUuidCommand,
		statusBarItem
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

	// トグルコマンドの登録
	let toggleScriptCommand = vscode.commands.registerCommand('hackcraft2.toggleScript', toggleScript);

	context.subscriptions.push(
		toggleScriptCommand
	);

	// エディタが変更されたときに実行状態を更新
	context.subscriptions.push(
		vscode.window.onDidChangeActiveTextEditor((editor) => {
			if (editor) {
				const language = getLanguageFromFile(editor.document.fileName);
				if (language) {
					vscode.commands.executeCommand('setContext', `hackcraft2.canRun${language.charAt(0).toUpperCase() + language.slice(1)}`, !isRunning);
				}
			}
		})
	);

	// 初期状態の設定
	updateConnectionState(false);
	updateRunningState(false);

	// 停止コマンドの登録
	let stopScriptCommand = vscode.commands.registerCommand('hackcraft2.stopScript', stopScript);

	context.subscriptions.push(
		stopScriptCommand
	);

	// 3Dビューを開くコマンドのハンドラを修正
	const open3DViewDisposable = vscode.commands.registerCommand("hackcraft2.open3DView", async () => {
		if (!isConnected || !selectedEntity || !loggedData) {
			vscode.window.showErrorMessage("Please connect and select an entity first");
			return;
		}

		// URLを構築
		const baseUrl = "http://hackcraft.jp/3dview/index.html";
		const params = new URLSearchParams({
			player_name: loggedData.player_name,
			player_id: loggedData.playerUUID,
			entity_name: selectedEntity.name,
			entity_id: selectedEntity.entityUuid,
			entity_type: selectedEntity.type,
			scratchPort: loggedData.scratchPort.toString(),
			monacoPort: loggedData.monacoPort.toString(),
			host: loggedData.host,
			port: loggedData.port.toString(),
			ssl: loggedData.ssl.toString(),
			level: loggedData.level.toString()
		});

		const url = `${baseUrl}?${params.toString()}`;
		vscode.env.openExternal(vscode.Uri.parse(url));
	});

	context.subscriptions.push(open3DViewDisposable);
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
