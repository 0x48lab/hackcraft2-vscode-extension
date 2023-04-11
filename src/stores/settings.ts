import { defineStore } from "pinia";

type SETTING = {
    serverAddress: string;
    playerId: string;
    serverAddressHistory: string[];
    playerIdHistory: string[];
};

// defineStore 関数を用いてストアを作成する
// 第一引数 "todos" はアプリケーション全体でストアを特定するためのユニークキー
export const useSettingStore = defineStore("settings", {
    // State は初期値を返す関数を定義する
    state: () => {
        return {
            setting: {} as SETTING,
        };
    },
    // getters は state 及び他の getter へのアクセスが可能
    // getter は全て computed 扱いになるため、引数に応じて結果を差し替える場合は関数を戻す
    getters: {
        current(state) {
            return state.setting;
        },
    },
    // mutations が存在しないので、State の更新は全て actions で行う
    actions: {
        load() {
            vscode.postMessage({
                command: 'loadConfig',
                data: {},
            })
        },
        // call from message dispatcher on App.vue
        onLoad(event: MessageEvent<any>) {
            const message = event.data // The JSON data our extension sent
            console.log('onLoad ' + message.command, message)
            const data = message.data

            this.setting.serverAddress = data.serverAddress
            this.setting.playerId = data.playerId
            this.setting.serverAddressHistory = data.serverAddressHistory
            this.setting.playerIdHistory = data.playerIdHistory

        },
        save() {
            vscode.postMessage({
                command: 'saveConfig',
                data: {
                    serverAddress: this.setting.serverAddress,
                    playerId: this.setting.playerId,
                },
            })
        },
        onSave(event: MessageEvent<any>) {
            const message = event.data // The JSON data our extension sent
            console.log('onSave ' + message.command, message)
            setTimeout(this.load, 500)
        }
    },
});