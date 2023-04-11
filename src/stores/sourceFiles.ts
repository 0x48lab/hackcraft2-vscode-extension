import { defineStore } from "pinia";

type SOURCE_FILE = {
    fileName: string;
    languageId: string;
    code: string
}

// defineStore 関数を用いてストアを作成する
// 第一引数 "sourceFiles" はアプリケーション全体でストアを特定するためのユニークキー
export const useSourceFileStore = defineStore("sourceFiles", {
    // State は初期値を返す関数を定義する
    state: () => {
        return {
            sourceFile: {} as SOURCE_FILE,
        };
    },
    // getters は state 及び他の getter へのアクセスが可能
    // getter は全て computed 扱いになるため、引数に応じて結果を差し替える場合は関数を戻す
    getters: {
        current(state) {
            return state.sourceFile;
        },
    },
    // mutations が存在しないので、State の更新は全て actions で行う
    actions: {
        getDocument() {
            vscode.postMessage({
                command: 'getCurrentDocument',
                data: {},
            })
        },
        onGetCurrentDocument(event: MessageEvent<any>) {
            const message = event.data // The JSON data our extension sent
            console.log('onGetCurrentDocument ' + message.command, message)
            const data = message.data

            this.sourceFile.fileName = data.fileName
            this.sourceFile.languageId = data.languageId
            this.sourceFile.code = data.code
        }
    },
});