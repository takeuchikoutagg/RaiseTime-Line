package com.example.raisetime.service;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// トランザクションが終わったあとに行う処理を登録する（S3 の画像の後始末に使う）。
// - afterCommit：DB の更新が確定してから（古い画像を消すとき。先に消すと、DB の更新が失敗したときに画像だけ無くなるため）
// - afterRollback：DB の更新が取り消されたら（アップロード済みの画像を消すとき。S3 にゴミを残さないため）
final class AfterTransaction {

    private AfterTransaction() {
    }

    static void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    static void afterRollback(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    action.run();
                }
            }
        });
    }
}
