import * as vscode from 'vscode'
import { BaseViewProvider } from './BaseViewProvider'


function createCompletionItem(name: string, args: string,  desc: string){
	const item = new vscode.CompletionItem(name, vscode.CompletionItemKind.Function);
	item.insertText = new vscode.SnippetString(args);
	item.documentation = new vscode.MarkdownString(desc);
  return item
}

export function activate(context: vscode.ExtensionContext) {

  //auto complete 
	const provider1 = vscode.languages.registerCompletionItemProvider('javascript', {

		provideCompletionItems(document: vscode.TextDocument, position: vscode.Position, token: vscode.CancellationToken, context: vscode.CompletionContext) {
			const commitCharacterCompletion = new vscode.CompletionItem('entity');
			commitCharacterCompletion.commitCharacters = ['.'];
			commitCharacterCompletion.documentation = new vscode.MarkdownString('Press `.` to get `entity.`');

			return [
				commitCharacterCompletion,
			];
		}
	});

  //todo このデータはTypeScriptの型定義ファイルから取得するようにしたい
	const provider2 = vscode.languages.registerCompletionItemProvider(
		'javascript',
		{
			provideCompletionItems(document: vscode.TextDocument, position: vscode.Position) {

				const linePrefix = document.lineAt(position).text.substr(0, position.character);
				if (!linePrefix.endsWith('entity.')) {
					return undefined;
				}

				return [
					createCompletionItem('getHeight', 'getHeight()', 'エンティティーの高さを返す'),
					createCompletionItem('getWidth', 'getWidth()', 'エンティティーの幅を返す'),
					createCompletionItem('getHealth', 'getHealth()', 'エンティティーの体力を返す'),
					createCompletionItem('getName', 'getName()', 'エンティティーの名前を返す'),
					createCompletionItem('getType', 'getType()', 'エンティティーの種類を返す'),
					createCompletionItem('uniqueId', 'uniqueId()', 'エンティティーのUUID'),
					createCompletionItem('setAI', 'setAI(${1:arg1})', 'AIを有効にする'),
					createCompletionItem('setup','setup()', 'エンティティーの位置をブロックにスナップし北を向いた状態で停止状態にする'),
					createCompletionItem('teardown','teardown()', 'エンティティーを停止状態から解除します'),
					createCompletionItem('getPosition','getPosition()', 'エンティティの位置を返す'),
					createCompletionItem('getBlockPosition','getBlockPosition()', 'エンティティーのブロックの位置を返す'),
					createCompletionItem('getOrientation','getOrientation()', 'エンティティーの頭の向きを返す'),
					createCompletionItem('getDirection','getDirection()', 'エンティティーの向きを返す'),
					createCompletionItem('getVelocity','getVelocity()', 'エンティティーの速度を返す'),
					createCompletionItem('teleport', 'teleport(${1:arg1}, ${2:arg2}, ${3:arg3})','指定した場所に移動'),
					createCompletionItem('move', 'move(${1:arg1})','向いている方向に加速度を与える'),
					createCompletionItem('forward', 'forward()','一歩前へ進む'),
					createCompletionItem('back', 'back()', '一歩後ろへ進む'),
					createCompletionItem('turnLeft','turnLeft()', '左を向く'),
					createCompletionItem('turnRight()','turnRight', '右を向く'),
					createCompletionItem('stepLeft','stepLeft()', '一歩左へステップ'),
					createCompletionItem('stepRight','stepRight()', '一歩右へステップ'),
					createCompletionItem('up', 'up()', '一歩上へ進む'),
					createCompletionItem('down','down()', '一歩下へ進む'),
					createCompletionItem('turn','turn(${1:arg1})', '指定した角度だけ向きを変える'),
					createCompletionItem('jump','jump()', 'ジャンプする'),
					createCompletionItem('interact','interact()', '右クリックアクション'),
					createCompletionItem('useItem','useItem(${1:arg1})', '指定されたスロットのアイテムを使う'),
					createCompletionItem('place','place(${1:arg1})', '指定されたスロットのアイテムを前に置く'),
					createCompletionItem('placeDown','placeDown(${1:arg1})', '指定されたスロットのアイテムを下に置く'),
					createCompletionItem('placeUp','placeUp(${1:arg1})', '指定されたスロットのアイテムを上に置く'),
					createCompletionItem('dig','dig()', '前を掘る'),
					createCompletionItem('digDown','digDown()', '下を掘る'),
					createCompletionItem('digUp','digUp()', '上を掘る'),
					createCompletionItem('isBlocked','isBlocked()', '前にブロックがあるかどうかを返す'),
					createCompletionItem('isBlockedUp','isBlockedUp()', '上にブロックがあるかどうかを返す'),
					createCompletionItem('isBlockedDown','isBlockedDown()', '下にブロックがあるかどうかを返す'),
					createCompletionItem('inspect','inspect(${1:arg1}, ${2:arg2}, ${3:arg3})', '自分の足元を中心に指定された座標のブロックの情報を返す'),
					createCompletionItem('getNearbyEntities','getNearbyEntities(${1:arg1}, ${2:arg2}, ${3:arg3})', '指定された範囲のエンティティーを返す'),
					createCompletionItem('stop','stop()', '動かないようにする'),
					createCompletionItem('setFacing', 'setFacing(${1:arg1})', 'エンティティーの向きを設定する'),
					createCompletionItem('distance','distance(${1:arg1}, ${2:arg2}, ${3:arg3})', '指定された座標までの距離を返す'),
					createCompletionItem('lookAt','lookAt(${1:arg1}, ${2:arg2}, ${3:arg3})', '指定された座標を向く'),
					createCompletionItem('setTarget','setTarget(${1:arg1})', 'ターゲットを攻撃する'),
					createCompletionItem('getItem','getItem(${1:arg1})', '指定されたスロットのアイテム情報を返す'),
					createCompletionItem('swapItem','swapItem(${1:arg1}, ${2:arg2})', '指定されたスロットのアイテムを交換する'),
					createCompletionItem('dropItem','dropItem(${1:arg1})', '指定されたスロットのアイテムをドロップする'),
					createCompletionItem('pickupItems','pickupItems()', '周辺のアイテムを拾う'),
					createCompletionItem('storeInChest','storeInChest()', '前あるいは下にあるインベントリにアイテムを格納する'),
				];
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
