// Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

import React from 'react'
import { List } from 'semantic-ui-react'
import { RealFac } from '@daml.js/realestate-facilitator';

type Props = {
  cashAccounts: RealFac.CashAccount.CashAccount.CashAccount[];
}

const CashAccountList: React.FC<Props> = ({cashAccounts}) => {
  return (
    <List divided verticalAlign='middle'>
      {[...cashAccounts].sort((x, y) => (x.serviceProvider+x.owner).localeCompare((y.serviceProvider+y.owner))).map(cashAccount =>
        <List.Item key={cashAccount.serviceProvider+cashAccount.owner}>
          <List.Content floated='right'><b>Available:</b> {+cashAccount.balance - +cashAccount.reserved}   <b>Reserved:</b> {cashAccount.reserved}</List.Content>
          <List.Icon name='money'/>
          <List.Content>{cashAccount.owner}@{cashAccount.serviceProvider}</List.Content>
        </List.Item>
      )}
    </List>
  );
};

export default CashAccountList;
