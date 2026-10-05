package com.yulinlin.data.core.session;

class BaseTransactionSession implements TransactionSession {
    private final TransactionScope transactions = new TransactionScope();

    @Override public void startTransaction() { transactions.start(); }
    @Override public void commitTransaction() { transactions.finish(false); }
    @Override public void rollbackTransaction() { transactions.finish(true); }
    @Override public boolean isOpenTransaction() { return transactions.isOpen(); }
    @Override public void setRollbackOnly() { transactions.setRollbackOnly(); }
    @Override public boolean isRollbackOnly() { return transactions.isRollbackOnly(); }
    protected int transactionDepth() { return transactions.depth(); }
    /** Opaque identity of this thread's outermost transaction; null outside a transaction. */
    public final Object transactionIdentity() { return transactions.identity(); }
    public final boolean isOutermostTransaction() { return transactions.depth() == 1; }
}
