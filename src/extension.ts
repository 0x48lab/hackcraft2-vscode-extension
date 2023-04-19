import * as vscode from 'vscode'
import { BaseViewProvider } from './BaseViewProvider'

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
	name: string;
	uuid: string;
	x: number;
	y: number;
	z: number;
}

interface ItemStack {
	slot: number;
	name: string;
	amount: number;
}


function createCompletionItem(name: string, args: string, desc: string, returnType: string): vscode.CompletionItem {
	const item = new vscode.CompletionItem(name, vscode.CompletionItemKind.Function);
	item.insertText = new vscode.SnippetString(args);
	item.documentation = new vscode.MarkdownString(desc);
	item.detail = returnType;
	return item
}

export function activate(context: vscode.ExtensionContext) {

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
						createCompletionItem('distance', 'distance(${1:arg1}, ${2:arg2}, ${3:arg3})', '指定された座標までの距離を返す', 'number'),
						createCompletionItem('lookAt', 'lookAt(${1:arg1}, ${2:arg2}, ${3:arg3})', '指定された座標を向く', 'void'),
						createCompletionItem('lockOnTarget', 'lockOnTarget(${1:arg1})', 'ターゲットを攻撃する', 'void'),
						createCompletionItem('teleport', 'teleport(${1:arg1}, ${2:arg2}, ${3:arg3})', '指定した場所に移動', 'void'),
						createCompletionItem('move', 'move(${1:arg1})', '向いている方向に加速度を与える', 'void'),
						createCompletionItem('turn', 'turn(${1:arg1})', '指定した角度だけ向きを変える', 'void'),
						createCompletionItem('forward', 'forward()', '一歩前へ進む', 'void'),
						createCompletionItem('back', 'back()', '一歩後ろへ進む', 'void'),
						createCompletionItem('up', 'up()', '一歩上へ進む', 'void'),
						createCompletionItem('down', 'down()', '一歩下へ進む', 'void'),
						createCompletionItem('turnLeft', 'turnLeft()', '左を向く', 'void'),
						createCompletionItem('turnRight()', 'turnRight()', '右を向く', 'void'),
						createCompletionItem('stepLeft', 'stepLeft()', '一歩左へステップ', 'void'),
						createCompletionItem('stepRight', 'stepRight()', '一歩右へステップ', 'void'),
						createCompletionItem('jump', 'jump()', 'ジャンプする', 'void'),
						createCompletionItem('grabItem', 'grabItem(${1:arg1})', '指定されたスロットのアイテムを持つ', 'boolean'),
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
		provider1, provider2,
		vscode.window.registerWebviewViewProvider(
			BaseViewProvider.viewType,
			provider
		)
	)
}
