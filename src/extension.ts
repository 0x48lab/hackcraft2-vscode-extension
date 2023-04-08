import * as vscode from 'vscode'
import { BaseViewProvider } from './BaseViewProvider'


function createCompletionItem(name: string, desc: string){
  const item = new vscode.CompletionItem(name, vscode.CompletionItemKind.Method)
  item.detail = desc
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
					createCompletionItem('setAI(bool)', 'AIを有効にする'),
					createCompletionItem('setup()', 'エンティティーの位置をブロックにスナップし北を向いた状態で停止状態にする'),
					createCompletionItem('teardown()', 'エンティティーを停止状態から解除します'),
					createCompletionItem('forward()', '一歩前へ進む'),
					createCompletionItem('back()', '一歩後ろへ進む'),
					createCompletionItem('turnLeft()', '左を向く'),
					createCompletionItem('turnRight()', '右を向く'),
					createCompletionItem('stepLeft()', '一歩左へステップ'),
					createCompletionItem('stepRight()', '一歩右へステップ'),
					createCompletionItem('up()', '一歩上へ進む'),
					createCompletionItem('down()', '一歩下へ進む'),
					createCompletionItem('jump()', 'ジャンプする'),
					createCompletionItem('interact()', '右クリックアクション'),
					createCompletionItem('useItem(int)', '指定されたスロットのアイテムを使う'),
					createCompletionItem('place(int)', '指定されたスロットのアイテムを前に置く'),
					createCompletionItem('placeDown(int)', '指定されたスロットのアイテムを下に置く'),
					createCompletionItem('placeUp(int)', '指定されたスロットのアイテムを上に置く'),
					createCompletionItem('dig()', '前を掘る'),
					createCompletionItem('digDown()', '下を掘る'),
					createCompletionItem('digUp()', '上を掘る'),
					createCompletionItem('canDig()', '前が掘ることができるかどうかを返す'),
					createCompletionItem('canDigUp()', '上が掘ることができるかどうかを返す'),
					createCompletionItem('canDigDown()', '下が掘ることができるかどうかを返す'),
					createCompletionItem('isBlocked()', '前にブロックがあるかどうかを返す'),
					createCompletionItem('isBlockedUp()', '上にブロックがあるかどうかを返す'),
					createCompletionItem('isBlockedDown()', '下にブロックがあるかどうかを返す'),
					createCompletionItem('inspect()', '前のブロックの情報を返す'),
					createCompletionItem('inspectUp()', '上のブロックの情報を返す'),
					createCompletionItem('inspectDown()', '下のブロックの情報を返す'),
					createCompletionItem('stop()', '動かないようにする'),
					createCompletionItem('setFacing(string)', 'エンティティーの向きを設定する'),
					createCompletionItem('setTarget(string)', 'ターゲットを攻撃する'),
					createCompletionItem('getItem(int)', '指定されたスロットのアイテム情報を返す'),
					createCompletionItem('swapItem(int, int)', '指定されたスロットのアイテムを交換する'),
					createCompletionItem('dropItem(int)', '指定されたスロットのアイテムをドロップする'),
					createCompletionItem('pickupItems()', '周辺のアイテムを拾う'),
					createCompletionItem('storeInChest()', '前あるいは下にあるインベントリにアイテムを格納する'),
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
