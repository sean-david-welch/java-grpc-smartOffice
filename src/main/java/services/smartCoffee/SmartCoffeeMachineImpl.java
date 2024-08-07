package services.smartCoffee;

import io.grpc.stub.StreamObserver;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class SmartCoffeeMachineImpl extends SmartCoffeeMachineGrpc.SmartCoffeeMachineImplBase {

    private final Connection conn;

    public SmartCoffeeMachineImpl(Connection conn) {
        this.conn = conn;
    }

    //  <-------- External Grpc methods -------->
    @Override
    public void brewCoffee(BrewCoffeeRequest request, StreamObserver<ActionResponse> responseObserver) {
        boolean brewed = brewCoffeeInternal(request);

        ActionResponse response = ActionResponse.newBuilder()
                .setSuccess(brewed)
                .setErrorCode(brewed ? ErrorCode.NONE : ErrorCode.SYSTEM_ERROR)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void checkInventory(CheckInventoryRequest request, StreamObserver<InventoryResponse> responseObserver) {
        try {
            if (request.hasItem()) {
                // Check specific item
                InventoryItem item = request.getItem();
                if (item != InventoryItem.UNKNOWN_ITEM && item != InventoryItem.UNRECOGNIZED) {
                    int quantity = getInventoryQuantity(item);
                    InventoryResponse response = InventoryResponse.newBuilder()
                            .setItem(item)
                            .setQuantity(quantity)
                            .setSuccess(true)
                            .build();
                    responseObserver.onNext(response);
                }
            } else {
                // Check all items
                for (InventoryItem item : InventoryItem.values()) {
                    if (item != InventoryItem.UNKNOWN_ITEM && item != InventoryItem.UNRECOGNIZED) {
                        int quantity = getInventoryQuantity(item);
                        InventoryResponse response = InventoryResponse.newBuilder()
                                .setItem(item)
                                .setQuantity(quantity)
                                .setSuccess(true)
                                .build();
                        responseObserver.onNext(response);
                    }
                }
            }
            responseObserver.onCompleted();
        } catch (SQLException e) {
            System.err.println("Error checking inventory: " + e.getMessage());
            responseObserver.onError(e);
        }
    }

    @Override
    public StreamObserver<RefillItemRequest> refillInventory(final StreamObserver<InventoryResponse> responseObserver) {
        return new StreamObserver<>() {
            private int totalItemsRefilled = 0;
            private InventoryItem lastItem = null;

            @Override
            public void onNext(RefillItemRequest request) {
                try {
                    int newQuantity = updateInventoryQuantity(request.getItem(), request.getQuantity());
                    totalItemsRefilled++;
                    lastItem = request.getItem();
                } catch (SQLException e) {
                    System.err.println("Error refilling inventory: " + e.getMessage());
                }
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("Error in refill: " + t.getMessage());
                responseObserver.onError(t);
            }

            @Override
            public void onCompleted() {
                InventoryResponse response = InventoryResponse.newBuilder()
                        .setItem(lastItem)
                        .setQuantity(totalItemsRefilled)
                        .setSuccess(true)
                        .build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }
        };
    }

    // <------- Interal Logic Methods ------->
    private boolean brewCoffeeInternal(BrewCoffeeRequest request) {
        CoffeeType type = request.getCoffeeType();

        int beansRequired;
        int waterRequired = 0;
        int milkRequired = 0;

        switch (type) {
            case AMERICANO:
                beansRequired = 10;
                waterRequired = 100;
                break;
            case CORTADO:
                beansRequired = 10;
                milkRequired = 50;
                break;
            case FLAT_WHITE:
                beansRequired = 10;
                milkRequired = 100;
                break;
            default:
                System.out.println("Unknown coffee type");
                return false;
        }

        try {
            conn.setAutoCommit(false);

            if (!(getInventoryQuantity(InventoryItem.COFFEE_BEANS) >= beansRequired &&
                    getInventoryQuantity(InventoryItem.WATER) >= waterRequired &&
                    getInventoryQuantity(InventoryItem.MILK) >= milkRequired)) {
                conn.rollback();
                System.out.println("Not enough stock, please refill and try again");
                return false;
            }

            updateInventoryQuantity(InventoryItem.COFFEE_BEANS, -beansRequired);
            if (waterRequired > 0) {
                updateInventoryQuantity(InventoryItem.WATER, -waterRequired);
            } else {
                updateInventoryQuantity(InventoryItem.MILK, -milkRequired);
            }

            conn.commit();
            return true;
        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException rollbackEx) {
                System.err.println("Error rolling back transaction: " + rollbackEx.getMessage());
            }
            System.err.println("Error brewing coffee: " + e.getMessage());
            return false;
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException e) {
                System.err.println("Error resetting auto-commit: " + e.getMessage());
            }
        }
    }

    private int updateInventoryQuantity(InventoryItem item, int change) throws SQLException {
        String sql = "update inventory_item set quantity = quantity + ? WHERE item = ?";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setInt(1, change);
            statement.setString(2, item.name());
            statement.executeUpdate();
            return getInventoryQuantity(item);
        } catch (SQLException e) {
            System.out.println("An error occurred while querying the database" + e.getMessage());
        }
        return 0;
    }

    private int getInventoryQuantity(InventoryItem item) throws SQLException {
        String sql = "select quantity from inventory_item where item = ?";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, item.name());
            ResultSet rs = statement.executeQuery();
            if (rs.next()) {
                return rs.getInt("quantity");
            }
        } catch (SQLException e) {
            System.out.println("error querying database" + e.getMessage());
        }
        return 0;
    }

}